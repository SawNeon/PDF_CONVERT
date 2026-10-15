package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class FormFillerTest {

    private final FormFiller filler = new FormFiller();

    @TempDir
    Path directory;

    @Test
    void inspectListsTheFillableFieldsAndSkipsReadOnlyOnes() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");

        FormFiller.FormInfo info = filler.inspect(form, null);

        Map<String, FormFiller.Field> byName = info.fields().stream()
                .collect(Collectors.toMap(FormFiller.Field::name, field -> field));
        assertEquals(List.of("aceito", "cidade", "nome", "observacoes", "sexo"),
                byName.keySet().stream().sorted().toList());
        assertEquals(FormFiller.FieldType.TEXT, byName.get("nome").type());
        assertTrue(byName.get("observacoes").multiline());
        assertEquals(20, byName.get("nome").maxLength());
        assertEquals(FormFiller.FieldType.CHECKBOX, byName.get("aceito").type());
        assertEquals(FormFiller.UNCHECKED, byName.get("aceito").value());
        assertEquals(List.of("Varginha", "Belo Horizonte"), byName.get("cidade").options());
        assertEquals(List.of("F", "M"), byName.get("sexo").options().stream().sorted().toList());
        assertFalse(byName.containsKey("protocolo"));
        assertFalse(info.hasSignatures());
        assertFalse(info.dynamicXfa());
    }

    @Test
    void fillWritesEveryTypeOfFieldIntoACopy() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        Path output = directory.resolve("filled.pdf");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("nome", "Maria da Conceição");
        values.put("observacoes", "linha 1\nlinha 2");
        values.put("aceito", FormFiller.CHECKED);
        values.put("cidade", "Belo Horizonte");
        values.put("sexo", "F");

        FormFiller.Result result = filler.fill(form, output, values, false, null);

        assertEquals(5, result.filled());
        assertFalse(result.flattened());
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            PDAcroForm saved = document.getDocumentCatalog().getAcroForm();
            assertEquals("Maria da Conceição", saved.getField("nome").getValueAsString());
            assertTrue(((PDCheckBox) saved.getField("aceito")).isChecked());
            assertEquals("[Belo Horizonte]", saved.getField("cidade").getValueAsString());
            assertEquals("F", saved.getField("sexo").getValueAsString());
        }
    }

    @Test
    void flatteningBurnsTheValuesIntoThePageAndRemovesTheFields() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        Path output = directory.resolve("flat.pdf");

        FormFiller.Result result = filler.fill(form, output, Map.of("nome", "Joana Souza"), true, null);

        assertTrue(result.flattened());
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            PDAcroForm remaining = document.getDocumentCatalog().getAcroForm();
            int fields = remaining == null ? 0 : remaining.getFields().size();
            long widgets = document.getPage(0).getAnnotations().stream()
                    .filter(PDAnnotationWidget.class::isInstance).count();
            assertEquals(0, fields, "fields left in the form");
            assertEquals(0, widgets, "widget annotations left on the page");
        }
        assertTrue(TestFiles.pageTexts(output).get(0).contains("Joana Souza"));
    }

    @Test
    void theOriginalFormIsNeverModified() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        String before = TestFiles.sha256(form);

        filler.fill(form, directory.resolve("filled.pdf"), Map.of("nome", "Alguém"), true, null);

        assertEquals(before, TestFiles.sha256(form));
    }

    @Test
    void readOnlyFieldsCannotBeChanged() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        Path output = directory.resolve("filled.pdf");

        FormFiller.Result result = filler.fill(form, output, Map.of("protocolo", "HACK"), false, null);

        assertEquals(0, result.filled());
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertEquals("123", document.getDocumentCatalog().getAcroForm().getField("protocolo").getValueAsString());
        }
    }

    @Test
    void valuesThatCannotBeEncodedOrAreTooLongAreRejected() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");

        assertThrows(PdfLocalException.class,
                () -> filler.fill(form, directory.resolve("a.pdf"), Map.of("nome", "機密"), false, null));
        assertThrows(PdfLocalException.class,
                () -> filler.fill(form, directory.resolve("b.pdf"), Map.of("nome", "x".repeat(21)), false, null));
        assertThrows(PdfLocalException.class,
                () -> filler.fill(form, directory.resolve("c.pdf"), Map.of("cidade", "Cidade Inexistente"), false, null));
        assertTrue(Files.notExists(directory.resolve("a.pdf")));
        assertTrue(Files.notExists(directory.resolve("b.pdf")));
    }

    @Test
    void aPdfWithoutAFormIsReportedClearly() throws Exception {
        Path plain = TestFiles.pdf(directory, "plain.pdf", "A1");

        assertTrue(filler.inspect(plain, null).fields().isEmpty());
        assertThrows(PdfLocalException.class,
                () -> filler.fill(plain, directory.resolve("out.pdf"), Map.of("nome", "x"), false, null));
    }

    @Test
    void theOutputCannotBeTheSource() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        String before = TestFiles.sha256(form);

        assertThrows(PdfLocalException.class, () -> filler.fill(form, form, Map.of("nome", "x"), false, null));
        assertEquals(before, TestFiles.sha256(form));
    }

    @Test
    void aProtectedFormAsksForThePassword() throws Exception {
        Path locked = TestFiles.encryptedPdf(directory, "locked.pdf", "abre-sesamo-2026");

        assertThrows(PasswordProtectedException.class, () -> filler.inspect(locked, null));
    }

    @Test
    void signaturesAreReported() throws Exception {
        Path signed = TestFiles.signedPdf(directory, "signed.pdf", "A1");

        assertTrue(filler.inspect(signed, null).hasSignatures());
    }

    @Test
    void thePasswordIsWipedAfterUse() throws Exception {
        Path form = TestFiles.formPdf(directory, "form.pdf");
        char[] password = "irrelevante-123".toCharArray();

        filler.inspect(form, password);

        assertEquals("\0".repeat(password.length), new String(password));
        assertNotNull(form);
    }
}
