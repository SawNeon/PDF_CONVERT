package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class ProtectStepTest {

    private static final String PASSWORD = "Senha-Forte-2026";

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void protectedPdfNeedsThePasswordAndUsesAes256() throws Exception {
        Path output = protectedOutput();

        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(output.toFile()));
        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(output.toFile(), "senha-errada-123"));
        try (PDDocument document = Loader.loadPDF(output.toFile(), PASSWORD)) {
            assertEquals(2, document.getNumberOfPages());
            assertTrue(document.isEncrypted());
            assertEquals(256, document.getEncryption().getLength());
            assertEquals(6, document.getEncryption().getRevision());
            assertTrue(new PDFTextStripper().getText(document).contains("A1"));
        }
    }

    @Test
    void thePasswordIsNotStoredInTheFileInPlainText() throws Exception {
        Path output = protectedOutput();

        String raw = new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("/Encrypt"));
        assertFalse(raw.contains(PASSWORD));
    }

    @Test
    void shortOrMissingPasswordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProtectStep("curta".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> new ProtectStep(new char[0]));
        assertThrows(IllegalArgumentException.class, () -> new ProtectStep("x".repeat(65).toCharArray()));
        assertThrows(NullPointerException.class, () -> new ProtectStep(null));
    }

    @Test
    void theCallerArrayIsNotModifiedAndTheStepKeepsItsOwnCopy() throws Exception {
        char[] password = PASSWORD.toCharArray();
        ProtectStep step = new ProtectStep(password);

        assertEquals(PASSWORD, new String(password));
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        Path output = directory.resolve("out.pdf");
        composer.compose(List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.ORIGINAL, List.of(step), output,
                SaveListener.NONE);

        try (PDDocument document = Loader.loadPDF(output.toFile(), PASSWORD)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    private Path protectedOutput() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1", "A2"));
        Path output = directory.resolve("protected.pdf");
        composer.compose(List.of(PageItem.pdfPage(source.id(), 0), PageItem.pdfPage(source.id(), 1)), registry,
                PageSize.ORIGINAL, List.of(new ProtectStep(PASSWORD.toCharArray())), output, SaveListener.NONE);
        return output;
    }
}
