package br.com.pdflocal.service;

import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import br.com.pdflocal.util.WrongPasswordException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class PdfRepairer {

    public record Report(int pagesFound, int pagesRecovered, int pagesWithProblems, boolean hadSignatures) {

        public int pagesLost() {
            return pagesFound - pagesRecovered;
        }
    }

    private static final int HEADER_SEARCH_BYTES = 1024;
    private static final byte[] HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final float CHECK_DPI = 24f;

    private static final Logger LOG = Logger.getLogger(PdfRepairer.class.getName());

    private final OutputStep cleanup = new StripThumbnailsStep();

    public Report repair(Path input, Path output, char[] password) throws PdfLocalException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        try {
            requirePdfHeader(input);
            Path directory = output.toAbsolutePath().getParent();
            if (directory == null || !Files.isDirectory(directory)) {
                throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
            }
            if (OutputFiles.isSameFile(input, output)) {
                throw new PdfLocalException(Messages.SAVE_OVERWRITES_SOURCE);
            }
            return rebuild(input, output, directory, password);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private Report rebuild(Path input, Path output, Path directory, char[] password) throws PdfLocalException {
        Path temporary = null;
        try (PDDocument source = load(input, password);
             PDDocument target = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            int found = pageCount(source);
            if (found <= 0) {
                throw new CorruptFileException(new IOException("No pages could be found"));
            }
            boolean signed = hasSignature(source);
            int recovered = 0;
            for (int index = 0; index < found; index++) {
                if (copyPage(source, target, index)) {
                    recovered++;
                }
            }
            if (recovered == 0) {
                throw new CorruptFileException(new IOException("No page could be recovered"));
            }
            int problems = countRenderProblems(target);
            cleanup.apply(target, SaveListener.NONE);

            temporary = Files.createTempFile(directory, "pdflocal-", ".tmp");
            target.save(temporary.toFile());
            OutputFiles.moveAtomically(temporary, output);
            temporary = null;
            LOG.log(Level.INFO, "PDF repaired: {0} of {1} pages recovered", new Object[] {recovered, found});
            return new Report(found, recovered, problems, signed);
        } catch (PdfLocalException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to repair PDF: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(DocumentComposer.classify(e, OutputFiles.usableSpace(directory)), e);
        } finally {
            OutputFiles.deleteQuietly(temporary);
        }
    }

    private static PDDocument load(Path input, char[] password) throws PdfLocalException {
        boolean hasPassword = password != null && password.length > 0;
        try {
            return Loader.loadPDF(input.toFile(), hasPassword ? new String(password) : "", null, null,
                    IOUtils.createTempFileOnlyStreamCache());
        } catch (InvalidPasswordException e) {
            throw hasPassword ? new WrongPasswordException(e) : new PasswordProtectedException(e);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to read PDF: {0}", e.getClass().getSimpleName());
            throw new CorruptFileException(e);
        }
    }

    private static void requirePdfHeader(Path input) throws PdfLocalException {
        byte[] head;
        try (InputStream in = Files.newInputStream(input)) {
            head = in.readNBytes(HEADER_SEARCH_BYTES);
        } catch (IOException e) {
            throw new CorruptFileException(e);
        }
        for (int i = 0; i + HEADER.length <= head.length; i++) {
            if (Arrays.equals(head, i, i + HEADER.length, HEADER, 0, HEADER.length)) {
                return;
            }
        }
        throw new UnsupportedFileException();
    }

    private static int pageCount(PDDocument source) {
        try {
            return source.getNumberOfPages();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean copyPage(PDDocument source, PDDocument target, int index) {
        try {
            PDPage page = source.getPage(index);
            target.importPage(page);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Page skipped: {0}", e.getClass().getSimpleName());
            return false;
        }
    }

    private static int countRenderProblems(PDDocument target) {
        PDFRenderer renderer = new PDFRenderer(target);
        int problems = 0;
        for (int index = 0; index < target.getNumberOfPages(); index++) {
            try {
                renderer.renderImageWithDPI(index, CHECK_DPI, ImageType.GRAY);
            } catch (IOException | RuntimeException e) {
                problems++;
            }
        }
        return problems;
    }

    private static boolean hasSignature(PDDocument source) {
        try {
            return !source.getSignatureDictionaries().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
