package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class ImageShareTest {

    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void scannedPdfIsMostlyImages() throws Exception {
        SourceDocument scan = registry.open(TestFiles.scannedPdf(directory, "scan.pdf", 2, 1500, 2100, 0.9f, false));

        ImageShare share = ImageShare.measure(
                List.of(PageItem.pdfPage(scan.id(), 0), PageItem.pdfPage(scan.id(), 1)), registry);

        assertTrue(share.share() > 0.9, "share " + share.share());
        assertFalse(share.isLow());
    }

    @Test
    void textOnlyPdfHasNoImageShare() throws Exception {
        SourceDocument text = registry.open(TestFiles.pdf(directory, "text.pdf", "A1", "A2"));

        ImageShare share = ImageShare.measure(
                List.of(PageItem.pdfPage(text.id(), 0), PageItem.pdfPage(text.id(), 1)), registry);

        assertEquals(0, share.imageBytes());
        assertTrue(share.isLow());
        assertEquals(0, share.percent());
    }

    @Test
    void mixingATextPdfWithAScanLowersTheShare() throws Exception {
        SourceDocument scan = registry.open(TestFiles.scannedPdf(directory, "scan.pdf", 1, 1500, 2100, 0.9f, false));
        SourceDocument text = registry.open(TestFiles.pdf(directory, "text.pdf", "A1"));
        long scanBytes = Files.size(scan.path());
        long textBytes = Files.size(text.path());

        ImageShare share = ImageShare.measure(
                List.of(PageItem.pdfPage(scan.id(), 0), PageItem.pdfPage(text.id(), 0)), registry);

        assertEquals(scanBytes + textBytes, share.totalBytes());
        assertTrue(share.share() < 1.0);
    }

    @Test
    void standaloneImageFilesCountEntirelyAsImages() throws Exception {
        Path photo = TestFiles.image(directory, "photo.png", "png", 300, 300);

        ImageShare share = ImageShare.measure(List.of(PageItem.image(photo)), registry);

        assertEquals(Files.size(photo), share.imageBytes());
        assertEquals(100, share.percent());
    }

    @Test
    void anImageSharedByManyPagesIsCountedOnce() throws Exception {
        SourceDocument shared = registry.open(TestFiles.sharedImagePdf(directory, "shared.pdf", 800, 800));
        int pages = registry.source(shared.id()).pageCount();

        ImageShare once = ImageShare.measure(List.of(PageItem.pdfPage(shared.id(), 0)), registry);
        ImageShare all = ImageShare.measure(
                java.util.stream.IntStream.range(0, pages).mapToObj(i -> PageItem.pdfPage(shared.id(), i)).toList(),
                registry);

        assertEquals(once.imageBytes(), all.imageBytes());
    }

    @Test
    void emptyListCountsAsFullShareSoNoWarningIsShown() {
        ImageShare share = ImageShare.measure(List.of(), registry);

        assertEquals(1.0, share.share());
        assertFalse(share.isLow());
    }
}
