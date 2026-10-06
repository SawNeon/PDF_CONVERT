package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class ThumbnailServiceTest {

    private static final long TIMEOUT_SECONDS = 30;

    private final SourceRegistry registry = new SourceRegistry();
    private final ThumbnailService service = new ThumbnailService(registry, 3);

    @TempDir
    Path directory;

    @AfterEach
    void tearDown() {
        service.close();
        registry.close();
    }

    @Test
    void rendersPdfPagesAtFiftyDpi() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));

        BufferedImage thumbnail = await(service.thumbnail(PageItem.pdfPage(source.id(), 0)));

        assertEquals(Math.round(612 * 50 / 72.0), thumbnail.getWidth(), 1);
        assertEquals(Math.round(792 * 50 / 72.0), thumbnail.getHeight(), 1);
    }

    @Test
    void renderingDpiIsConfigurable() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        try (ThumbnailService preview = new ThumbnailService(registry, 2, 110)) {
            BufferedImage large = await(preview.thumbnail(PageItem.pdfPage(source.id(), 0)));

            assertEquals(Math.round(612 * 110 / 72.0), large.getWidth(), 1);
            assertEquals(Math.round(792 * 110 / 72.0), large.getHeight(), 1);
        }
    }

    @Test
    void imageSideLimitScalesWithTheDpi() throws Exception {
        Path image = TestFiles.image(directory, "wide.png", "png", 4000, 2000);
        try (ThumbnailService preview = new ThumbnailService(registry, 2, 110)) {
            BufferedImage large = await(preview.thumbnail(PageItem.image(image)));

            assertEquals(Math.round(ThumbnailService.IMAGE_MAX_SIDE * 110 / 50.0), large.getWidth(), 1);
        }
    }

    @Test
    void rejectsNonPositiveDpi() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ThumbnailService(registry, 2, 0));
    }

    @Test
    void largeImagesAreScaledDownKeepingProportion() throws Exception {
        Path image = TestFiles.image(directory, "wide.png", "png", 2000, 1000);

        BufferedImage thumbnail = await(service.thumbnail(PageItem.image(image)));

        assertEquals(ThumbnailService.IMAGE_MAX_SIDE, thumbnail.getWidth());
        assertEquals(Math.round(ThumbnailService.IMAGE_MAX_SIDE / 2.0), thumbnail.getHeight(), 1);
    }

    @Test
    void smallImagesAreNotEnlarged() throws Exception {
        Path image = TestFiles.image(directory, "small.png", "png", 40, 20);

        BufferedImage thumbnail = await(service.thumbnail(PageItem.image(image)));

        assertEquals(40, thumbnail.getWidth());
        assertEquals(20, thumbnail.getHeight());
    }

    @Test
    void imageThumbnailsApplyExifOrientation() throws Exception {
        Path image = TestFiles.jpegWithOrientation(directory, "photo.jpg", 80, 40, 6);

        BufferedImage thumbnail = await(service.thumbnail(PageItem.image(image)));

        assertEquals(40, thumbnail.getWidth());
        assertEquals(80, thumbnail.getHeight());
        assertEquals(TestFiles.BLUE, TestFiles.dominantColor(thumbnail.getRGB(10, 20)));
        assertEquals(TestFiles.RED, TestFiles.dominantColor(thumbnail.getRGB(30, 20)));
        assertEquals(TestFiles.YELLOW, TestFiles.dominantColor(thumbnail.getRGB(10, 60)));
        assertEquals(TestFiles.GREEN, TestFiles.dominantColor(thumbnail.getRGB(30, 60)));
    }

    @Test
    void everySupportedImageFormatHasAThumbnail() throws Exception {
        for (String format : List.of("jpg", "png", "gif", "bmp", "tiff")) {
            Path image = TestFiles.image(directory, "image." + format, format, 60, 30);

            BufferedImage thumbnail = await(service.thumbnail(PageItem.image(image)));

            assertEquals(60, thumbnail.getWidth(), format);
            assertEquals(30, thumbnail.getHeight(), format);
        }
    }

    @Test
    void rotationDoesNotChangeTheCachedThumbnail() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PageItem page = PageItem.pdfPage(source.id(), 0);

        BufferedImage first = await(service.thumbnail(page));
        BufferedImage second = await(service.thumbnail(page.rotatedBy(90)));

        assertSame(first, second);
    }

    @Test
    void secondRequestIsServedFromTheCache() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PageItem page = PageItem.pdfPage(source.id(), 0);

        BufferedImage first = await(service.thumbnail(page));
        CompletableFuture<BufferedImage> second = service.thumbnail(page);

        assertTrue(second.isDone());
        assertSame(first, second.get());
    }

    @Test
    void concurrentRequestsForTheSamePageShareTheRendering() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PageItem page = PageItem.pdfPage(source.id(), 0);

        CompletableFuture<BufferedImage> first = service.thumbnail(page);
        CompletableFuture<BufferedImage> second = service.thumbnail(page);

        assertSame(await(first), await(second));
    }

    @Test
    void leastRecentlyUsedThumbnailsAreEvicted() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "1", "2", "3", "4"));
        BufferedImage firstRender = await(service.thumbnail(PageItem.pdfPage(source.id(), 0)));
        for (int page = 1; page <= 3; page++) {
            await(service.thumbnail(PageItem.pdfPage(source.id(), page)));
        }

        BufferedImage again = await(service.thumbnail(PageItem.pdfPage(source.id(), 0)));

        assertNotSame(firstRender, again);
        assertEquals(firstRender.getWidth(), again.getWidth());
    }

    @Test
    void manyConcurrentRequestsOnOneDocumentAllSucceed() throws Exception {
        String[] labels = new String[40];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = "page " + i;
        }
        SourceDocument source = registry.open(TestFiles.pdf(directory, "big.pdf", labels));

        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>();
        for (int page = 0; page < labels.length; page++) {
            futures.add(service.thumbnail(PageItem.pdfPage(source.id(), page)));
        }

        for (CompletableFuture<BufferedImage> future : futures) {
            assertEquals(425, await(future).getWidth(), 1);
        }
    }

    @Test
    void differentDocumentsRenderIndependently() throws Exception {
        SourceDocument first = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        SourceDocument second = registry.open(TestFiles.pdf(directory, "b.pdf", "B1"));

        CompletableFuture<BufferedImage> a = service.thumbnail(PageItem.pdfPage(first.id(), 0));
        CompletableFuture<BufferedImage> b = service.thumbnail(PageItem.pdfPage(second.id(), 0));

        assertNotSame(await(a), await(b));
    }

    @Test
    void releaseDropsCachedThumbnailsOfThatDocumentOnly() throws Exception {
        SourceDocument first = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        SourceDocument second = registry.open(TestFiles.pdf(directory, "b.pdf", "B1"));
        BufferedImage firstBefore = await(service.thumbnail(PageItem.pdfPage(first.id(), 0)));
        BufferedImage secondBefore = await(service.thumbnail(PageItem.pdfPage(second.id(), 0)));

        service.release(first.id());

        assertNotSame(firstBefore, await(service.thumbnail(PageItem.pdfPage(first.id(), 0))));
        assertSame(secondBefore, await(service.thumbnail(PageItem.pdfPage(second.id(), 0))));
    }

    @Test
    void requestForAClosedDocumentFails() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PageItem page = PageItem.pdfPage(source.id(), 0);
        service.release(source.id());
        registry.close(source.id());

        CompletableFuture<BufferedImage> future = service.thumbnail(page);

        assertThrows(ExecutionException.class, () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void undecodableImageFails() throws Exception {
        Path valid = TestFiles.image(directory, "valid.png", "png", 50, 50);
        Path truncated = directory.resolve("truncated.png");
        java.nio.file.Files.write(truncated, java.util.Arrays.copyOf(java.nio.file.Files.readAllBytes(valid), 40));

        CompletableFuture<BufferedImage> future = service.thumbnail(PageItem.image(truncated));

        assertThrows(ExecutionException.class, () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void closedServiceRejectsNewRequests() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        service.close();

        CompletableFuture<BufferedImage> future = service.thumbnail(PageItem.pdfPage(source.id(), 0));

        assertThrows(ExecutionException.class, () -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void releasingCancelsRequestsStillWaiting() throws Exception {
        String[] labels = new String[30];
        java.util.Arrays.fill(labels, "x");
        SourceDocument source = registry.open(TestFiles.pdf(directory, "big.pdf", labels));
        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>();
        for (int page = 0; page < labels.length; page++) {
            futures.add(service.thumbnail(PageItem.pdfPage(source.id(), page)));
        }

        service.release(source.id());

        for (CompletableFuture<BufferedImage> future : futures) {
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (CancellationException | ExecutionException expected) {
                assertTrue(future.isCancelled() || future.isCompletedExceptionally());
            }
        }
    }

    private static BufferedImage await(CompletableFuture<BufferedImage> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
