package br.com.pdflocal.service;

import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

public final class FormFiller {

    public enum FieldType {
        TEXT,
        CHECKBOX,
        RADIO,
        CHOICE
    }

    public record Field(String name, FieldType type, String value, List<String> options, boolean multiline,
                        int maxLength) {
    }

    public record FormInfo(List<Field> fields, boolean hasSignatures, boolean dynamicXfa) {
    }

    public record Result(int filled, boolean flattened, boolean hadSignatures) {
    }

    public static final String CHECKED = "true";
    public static final String UNCHECKED = "false";
    static final int MAX_FIELDS = 500;
    static final int MAX_VALUE_LENGTH = 2000;

    private static final Logger LOG = Logger.getLogger(FormFiller.class.getName());

    private final OutputStep cleanup = new StripThumbnailsStep();

    public FormInfo inspect(Path input, char[] password) throws PdfLocalException {
        Objects.requireNonNull(input, "input");
        try (PDDocument document = PdfLoader.open(input, password)) {
            boolean signed = PdfLoader.hasSignature(document);
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                return new FormInfo(List.of(), signed, false);
            }
            List<Field> fields = new ArrayList<>();
            for (PDField field : form.getFieldTree()) {
                if (fields.size() >= MAX_FIELDS) {
                    break;
                }
                describe(field).ifPresent(fields::add);
            }
            return new FormInfo(List.copyOf(fields), signed, form.xfaIsDynamic());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to inspect the form: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(Messages.FILE_UNREADABLE, e);
        } finally {
            wipe(password);
        }
    }

    public Result fill(Path input, Path output, Map<String, String> values, boolean flatten, char[] password)
            throws PdfLocalException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(values, "values");
        Path directory = output.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            wipe(password);
            throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
        }
        if (OutputFiles.isSameFile(input, output)) {
            wipe(password);
            throw new PdfLocalException(Messages.SAVE_OVERWRITES_SOURCE);
        }
        Path temporary = null;
        try (PDDocument document = PdfLoader.open(input, password)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                throw new PdfLocalException(Messages.FORM_MISSING);
            }
            if (form.xfaIsDynamic()) {
                throw new PdfLocalException(Messages.FORM_XFA);
            }
            boolean signed = PdfLoader.hasSignature(document);
            int filled = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                PDField field = form.getField(entry.getKey());
                if (field != null && apply(field, entry.getValue())) {
                    filled++;
                }
            }
            if (form.hasXFA()) {
                form.setXFA(null);
            }
            if (flatten) {
                form.flatten(form.getFields(), true);
            } else {
                refresh(form);
            }
            cleanup.apply(document, SaveListener.NONE);

            temporary = Files.createTempFile(directory, "pdflocal-", ".tmp");
            document.save(temporary.toFile());
            OutputFiles.moveAtomically(temporary, output);
            temporary = null;
            LOG.log(Level.INFO, "Form filled with {0} fields", filled);
            return new Result(filled, flatten, signed);
        } catch (PdfLocalException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to fill the form: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(DocumentComposer.classify(e, OutputFiles.usableSpace(directory)), e);
        } finally {
            OutputFiles.deleteQuietly(temporary);
            wipe(password);
        }
    }

    private static Optional<Field> describe(PDField field) throws IOException {
        if (!(field instanceof PDTerminalField) || field.isReadOnly()) {
            return Optional.empty();
        }
        String name = field.getFullyQualifiedName();
        if (field instanceof PDTextField text) {
            if (text.isPassword() || text.isFileSelect()) {
                return Optional.empty();
            }
            return Optional.of(new Field(name, FieldType.TEXT, nullToEmpty(text.getValue()), List.of(),
                    text.isMultiline(), text.getMaxLen()));
        }
        if (field instanceof PDCheckBox box) {
            return Optional.of(new Field(name, FieldType.CHECKBOX, box.isChecked() ? CHECKED : UNCHECKED, List.of(),
                    false, -1));
        }
        if (field instanceof PDRadioButton radio) {
            List<String> options = new ArrayList<>(radio.getOnValues());
            String value = nullToEmpty(radio.getValue());
            return Optional.of(new Field(name, FieldType.RADIO, "Off".equals(value) ? "" : value, options, false, -1));
        }
        if (field instanceof PDChoice choice && !choice.isMultiSelect()) {
            List<String> selected = choice.getValue();
            return Optional.of(new Field(name, FieldType.CHOICE, selected.isEmpty() ? "" : selected.get(0),
                    List.copyOf(optionsOf(choice)), false, -1));
        }
        return Optional.empty();
    }

    private static boolean apply(PDField field, String value) throws PdfLocalException {
        if (value == null || field.isReadOnly()) {
            return false;
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new PdfLocalException(Messages.FORM_VALUE_INVALID);
        }
        try {
            if (field instanceof PDTextField text) {
                if (text.getMaxLen() > 0 && value.length() > text.getMaxLen()) {
                    throw new PdfLocalException(Messages.FORM_VALUE_INVALID);
                }
                text.setValue(value);
                return true;
            }
            if (field instanceof PDCheckBox box) {
                if (CHECKED.equals(value)) {
                    box.check();
                } else {
                    box.unCheck();
                }
                return true;
            }
            if (field instanceof PDRadioButton radio) {
                if (value.isEmpty()) {
                    return false;
                }
                if (!radio.getOnValues().contains(value)) {
                    throw new PdfLocalException(Messages.FORM_VALUE_INVALID);
                }
                radio.setValue(value);
                return true;
            }
            if (field instanceof PDChoice choice && !choice.isMultiSelect()) {
                if (value.isEmpty()) {
                    return false;
                }
                boolean editable = choice instanceof PDComboBox combo && combo.isEdit();
                if (!editable && !optionsOf(choice).contains(value)) {
                    throw new PdfLocalException(Messages.FORM_VALUE_INVALID);
                }
                choice.setValue(value);
                return true;
            }
            return false;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Form value rejected: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(Messages.FORM_VALUE_INVALID, e);
        }
    }

    private static List<String> optionsOf(PDChoice choice) {
        return choice.getOptionsExportValues().isEmpty() ? choice.getOptions() : choice.getOptionsExportValues();
    }

    private static void refresh(PDAcroForm form) {
        try {
            form.refreshAppearances();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Appearances left to the viewer: {0}", e.getClass().getSimpleName());
            form.setNeedAppearances(true);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
