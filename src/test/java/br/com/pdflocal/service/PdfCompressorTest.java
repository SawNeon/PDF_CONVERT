package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.SaveCancelledException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfCompressorTest {

    private static final int WIDTH = 2000;
    private static final int HEIGHT = 2800;
    private static final double A4_LONG_SIDE_INCHES = 842.0 / 72.0;

    private final PdfCompressor compressor = new PdfCompressor();

    @TempDir
    Path directory;

    @Test
    void noneLevelChangesNothing() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 1, WIDTH, HEIGHT, 0.9f, false);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            CompressionStats stats = compressor.compress(document, CompressionLevel.NONE, SaveListener.NONE);

            assertEquals(CompressionStats.EMPTY, stats);
            assertEquals(WIDTH, TestFiles.imagesOf(document).get(0).getWidth());
        }
    }

    @Test
    void strongLevelShrinksTheFileAndTheImageResolution() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 2, WIDTH, HEIGHT, 0.9f, false);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            CompressionStats stats = compressor.compress(document, CompressionLevel.STRONG, SaveListener.NONE);
            Path output = directory.resolve("out.pdf");
            document.save(output.toFile());

            assertEquals(2, stats.images());
            assertEquals(2, stats.replaced());
            assertTrue(stats.bytesAfter() < stats.bytesBefore() / 2, stats.toString());
            assertTrue(Files.size(output) < Files.size(source) / 2);
            double maxSide = 100 * A4_LONG_SIDE_INCHES + 2;
            for (PDImageXObject image : TestFiles.imagesOf(document)) {
                assertTrue(Math.max(image.getWidth(), image.getHeight()) <= maxSide,
                        "image side " + image.getHeight());
            }
        }
    }

    @Test
    void levelsProduceDecreasingFileSizes() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 1, WIDTH, HEIGHT, 0.9f, false);
        long light = sizeAfter(source, CompressionLevel.LIGHT);
        long medium = sizeAfter(source, CompressionLevel.MEDIUM);
        long strong = sizeAfter(source, CompressionLevel.STRONG);

        assertTrue(light < Files.size(source), "light " + light);
        assertTrue(medium < light, "medium " + medium + " light " + light);
        assertTrue(strong < medium, "strong " + strong + " medium " + medium);
    }

    @Test
    void pageSizeAndPageCountAreKept() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 3, WIDTH, HEIGHT, 0.9f, false);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            float width = document.getPage(0).getMediaBox().getWidth();
            float height = document.getPage(0).getMediaBox().getHeight();

            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            assertEquals(3, document.getNumberOfPages());
            assertEquals(width, document.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(height, document.getPage(0).getMediaBox().getHeight(), 0.01);
        }
    }

    @Test
    void grayScansStayGray() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "gray.pdf", 1, WIDTH, HEIGHT, 0.9f, true);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            PDImageXObject image = TestFiles.imagesOf(document).get(0);
            assertEquals(1, image.getColorSpace().getNumberOfComponents());
            assertTrue(image.getWidth() < WIDTH);
        }
    }

    @Test
    void imagesSmallerThanTheTargetAreNeverEnlarged() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "small.pdf", 1, 800, 1100, 0.95f, false);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.LIGHT, SaveListener.NONE);

            PDImageXObject image = TestFiles.imagesOf(document).get(0);
            assertTrue(image.getWidth() <= 800);
            assertTrue(image.getHeight() <= 1100);
        }
    }

    @Test
    void keepsTheOriginalWhenTheNewImageWouldNotBeSmaller() throws Exception {
        Path source = TestFiles.noisyJpegPdf(directory, "noise.pdf", 400, 0.1f);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDImageXObject before = TestFiles.imagesOf(document).get(0);
            long originalLength = before.getCOSObject().getLength();

            CompressionStats stats = compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            assertEquals(0, stats.replaced());
            assertEquals(originalLength, TestFiles.imagesOf(document).get(0).getCOSObject().getLength());
        }
    }

    @Test
    void imagesWithTransparencyAreLeftUntouched() throws Exception {
        Path source = TestFiles.transparentImagePdf(directory, "alpha.pdf", 1800);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            PDImageXObject before = TestFiles.imagesOf(document).get(0);
            assertTrue(before.getSoftMask() != null);

            CompressionStats stats = compressor.compress(document, CompressionLevel.STRONG, SaveListener.NONE);

            assertEquals(1, stats.images());
            assertEquals(0, stats.replaced());
            assertEquals(1800, TestFiles.imagesOf(document).get(0).getWidth());
        }
    }

    @Test
    void anImageUsedOnTwoPagesIsProcessedOnceAndStaysShared() throws Exception {
        Path source = TestFiles.sharedImagePdf(directory, "shared.pdf", WIDTH, HEIGHT);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            CompressionStats stats = compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            assertEquals(2, stats.images());
            assertEquals(1, stats.replaced());
            List<PDImageXObject> images = TestFiles.imagesOf(document);
            assertSame(images.get(0).getCOSObject(), images.get(1).getCOSObject());
            assertTrue(images.get(0).getWidth() < WIDTH);
        }
    }

    @Test
    void textOnTheSamePageIsPreserved() throws Exception {
        Path source = TestFiles.scannedPageWithText(directory, "text.pdf", "Laudo medico", WIDTH, HEIGHT, 0.9f);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.STRONG, SaveListener.NONE);
            document.save(directory.resolve("out.pdf").toFile());
        }

        assertEquals(List.of("Laudo medico"), TestFiles.pageTexts(directory.resolve("out.pdf")));
    }

    @Test
    void renderedPageStaysVisuallyClose() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 1, WIDTH, HEIGHT, 0.9f, false);
        Path output = directory.resolve("out.pdf");

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);
            document.save(output.toFile());
        }

        double difference = TestFiles.meanDifference(TestFiles.render(source, 0), TestFiles.render(output, 0));
        assertTrue(difference < 6.0, "mean difference " + difference);
    }

    @Test
    void reportsProgressForEachImage() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 3, 1400, 1960, 0.9f, false);
        List<String> progress = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, listener(progress, () -> false));
        }

        assertEquals(List.of("1/3", "2/3", "3/3"), progress);
    }

    @Test
    void cancellingStopsBeforeTheNextImage() throws Exception {
        Path source = TestFiles.scannedPdf(directory, "a.pdf", 3, 1400, 1960, 0.9f, false);
        List<String> progress = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            assertThrows(SaveCancelledException.class, () -> compressor.compress(
                    document, CompressionLevel.MEDIUM, listener(progress, () -> progress.size() >= 1)));
        }

        assertEquals(List.of("1/3"), progress);
    }

    @Test
    void documentWithoutImagesIsReportedAsEmpty() throws Exception {
        Path source = TestFiles.pdf(directory, "text.pdf", "only text");

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            CompressionStats stats = compressor.compress(document, CompressionLevel.STRONG, SaveListener.NONE);

            assertEquals(0, stats.images());
            assertFalse(stats.savedBytes() > 0);
        }
    }

    private long sizeAfter(Path source, CompressionLevel level) throws Exception {
        Path output = directory.resolve("out-" + level + ".pdf");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, level, SaveListener.NONE);
            document.save(output.toFile());
        }
        return Files.size(output);
    }

    private static SaveListener listener(List<String> progress, java.util.function.BooleanSupplier cancelled) {
        return new SaveListener() {
            @Override
            public void onPage(int done, int total) {
            }

            @Override
            public void onCompress(int done, int total) {
                progress.add(done + "/" + total);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.getAsBoolean();
            }
        };
    }
}
