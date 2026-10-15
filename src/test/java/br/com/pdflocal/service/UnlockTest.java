package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class UnlockTest {

    private static final String PASSWORD = "abre-sesamo-2026";

    private final SourceRegistry registry = new SourceRegistry();
    private final ImportService importer = new ImportService(registry);

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void aProtectedPdfIsReportedAsNeedingAPassword() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", PASSWORD);

        ImportResult result = importer.importFiles(java.util.List.of(file));

        assertTrue(result.items().isEmpty());
        ImportResult.Failure failure = result.failures().get(0);
        assertEquals(ImportResult.Reason.PASSWORD_REQUIRED, failure.reason());
        assertTrue(failure.needsPassword());
        assertEquals(file, failure.file());
    }

    @Test
    void theRightPasswordOpensThePdf() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", PASSWORD);

        ImportResult result = importer.importProtected(file, PASSWORD.toCharArray());

        assertTrue(result.failures().isEmpty());
        assertEquals(1, result.items().size());
    }

    @Test
    void aWrongPasswordIsReportedSoTheUserCanTryAgain() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", PASSWORD);

        ImportResult result = importer.importProtected(file, "senha-errada-1".toCharArray());

        assertTrue(result.items().isEmpty());
        assertEquals(ImportResult.Reason.PASSWORD_WRONG, result.failures().get(0).reason());
        assertTrue(result.failures().get(0).needsPassword());
    }

    @Test
    void thePasswordArrayIsWipedAfterUse() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", PASSWORD);
        char[] password = PASSWORD.toCharArray();

        importer.importProtected(file, password);

        char[] wiped = new char[PASSWORD.length()];
        Arrays.fill(wiped, '\0');
        assertTrue(Arrays.equals(wiped, password));
    }

    @Test
    void theGeneratedPdfFromAnUnlockedSourceHasNoPassword() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", PASSWORD);
        ImportResult imported = importer.importProtected(file, PASSWORD.toCharArray());
        Path output = directory.resolve("open.pdf");

        new DocumentComposer().compose(imported.items(), registry, PageSize.ORIGINAL, output);

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertFalse(document.isEncrypted());
        }
        assertEquals("secret", TestFiles.pageTexts(output).get(0));
    }

    @Test
    void plainFailuresDoNotAskForAPassword() throws Exception {
        Path corrupt = TestFiles.corruptPdf(directory, "broken.pdf");

        ImportResult result = importer.importFiles(java.util.List.of(corrupt));

        assertFalse(result.failures().get(0).needsPassword());
    }
}
