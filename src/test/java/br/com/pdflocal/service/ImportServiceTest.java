package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class ImportServiceTest {

    private final SourceRegistry registry = new SourceRegistry();
    private final ImportService service = new ImportService(registry);

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void importsPdfPagesAndImagesKeepingTheGivenOrder() throws Exception {
        Path a = TestFiles.pdf(directory, "a.pdf", "A1", "A2");
        Path photo = TestFiles.image(directory, "photo.jpg", "jpg", 20, 20);
        Path b = TestFiles.pdf(directory, "b.pdf", "B1");

        ImportResult result = service.importFiles(List.of(a, photo, b));

        assertTrue(result.failures().isEmpty());
        List<PageItem> items = result.items();
        assertEquals(4, items.size());
        assertEquals(0, items.get(0).pageIndex());
        assertEquals(1, items.get(1).pageIndex());
        assertEquals(items.get(0).sourceId(), items.get(1).sourceId());
        assertTrue(items.get(2).isImage());
        assertEquals(photo, items.get(2).imagePath());
        assertFalse(items.get(3).isImage());
        assertEquals(2, registry.sources().size());
    }

    @Test
    void importsEverySupportedImageFormat() throws Exception {
        List<Path> images = List.of(
                TestFiles.image(directory, "a.jpg", "jpg", 10, 10),
                TestFiles.image(directory, "b.png", "png", 10, 10),
                TestFiles.image(directory, "c.gif", "gif", 10, 10),
                TestFiles.image(directory, "d.bmp", "bmp", 10, 10),
                TestFiles.image(directory, "e.tiff", "tiff", 10, 10));

        ImportResult result = service.importFiles(images);

        assertTrue(result.failures().isEmpty());
        assertEquals(5, result.items().size());
    }

    @Test
    void badFilesAreReportedAndTheOthersStillImported() throws Exception {
        Path good = TestFiles.pdf(directory, "good.pdf", "G1", "G2");
        Path fake = TestFiles.fakeFile(directory, "fake.pdf", "not a pdf");
        Path corrupt = TestFiles.corruptPdf(directory, "broken.pdf");
        Path locked = TestFiles.encryptedPdf(directory, "locked.pdf", "s3cret");
        Path photo = TestFiles.image(directory, "photo.png", "png", 10, 10);

        ImportResult result = service.importFiles(List.of(fake, good, corrupt, locked, photo));

        assertEquals(3, result.items().size());
        assertEquals(3, result.failures().size());
        assertEquals("fake.pdf", result.failures().get(0).fileName());
        assertEquals("Tipo de arquivo não suportado. Use PDF ou imagens (JPEG, PNG, GIF, BMP, TIFF).",
                result.failures().get(0).message());
        assertEquals("broken.pdf", result.failures().get(1).fileName());
        assertEquals("Não foi possível ler o arquivo", result.failures().get(1).message());
        assertEquals("locked.pdf", result.failures().get(2).fileName());
        assertEquals("PDF protegido por senha", result.failures().get(2).message());
    }

    @Test
    void failedPdfsAreNotLeftOpen() throws Exception {
        Path corrupt = TestFiles.corruptPdf(directory, "broken.pdf");
        Path locked = TestFiles.encryptedPdf(directory, "locked.pdf", "s3cret");

        service.importFiles(List.of(corrupt, locked));

        assertTrue(registry.sources().isEmpty());
    }

    @Test
    void aFolderIsReportedAsUnreadable() throws IOException {
        Path folder = Files.createDirectory(directory.resolve("folder"));

        ImportResult result = service.importFiles(List.of(folder));

        assertTrue(result.items().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("folder", result.failures().get(0).fileName());
    }

    @Test
    void missingFileIsReported() {
        ImportResult result = service.importFiles(List.of(directory.resolve("missing.pdf")));

        assertTrue(result.items().isEmpty());
        assertEquals("Não foi possível ler o arquivo", result.failures().get(0).message());
    }

    @Test
    void emptyInputGivesEmptyResult() {
        ImportResult result = service.importFiles(List.of());

        assertTrue(result.items().isEmpty());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void neverModifiesTheImportedFiles() throws Exception {
        Path pdf = TestFiles.pdf(directory, "a.pdf", "A1");
        Path photo = TestFiles.image(directory, "photo.jpg", "jpg", 10, 10);
        String pdfHash = TestFiles.sha256(pdf);
        String photoHash = TestFiles.sha256(photo);

        service.importFiles(List.of(pdf, photo));
        registry.close();

        assertEquals(pdfHash, TestFiles.sha256(pdf));
        assertEquals(photoHash, TestFiles.sha256(photo));
    }
}
