package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfCompressorResolutionTest {

    private static final double A4_WIDTH = PDRectangle.A4.getWidth();
    private static final double A4_HEIGHT = PDRectangle.A4.getHeight();

    private final PdfCompressor compressor = new PdfCompressor();

    @TempDir
    Path directory;

    @Test
    void theSameImageIsMeasuredAtItsRealDrawnSize() throws Exception {
        Path full = placed("full.pdf", 2000, 2800, A4_WIDTH, A4_HEIGHT);
        Path half = placed("half.pdf", 2000, 2800, A4_WIDTH / 2, A4_HEIGHT / 2);

        assertEquals(2800 / (A4_HEIGHT / 72), onlyResolution(full), 1.0);
        assertEquals(2800 / (A4_HEIGHT / 144), onlyResolution(half), 1.0);
    }

    @Test
    void imageInsideAFormIsMeasuredWithTheFormScale() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = JPEGFactory.createFromImage(document, TestFiles.syntheticScan(600, 600, 1, false), 0.9f);
            PDAppearanceStream form = new PDAppearanceStream(document);
            form.setBBox(new PDRectangle(0, 0, 100, 100));
            form.setResources(new PDResources());
            try (PDPageContentStream content = new PDPageContentStream(document, form)) {
                content.drawImage(image, 0, 0, 100, 100);
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.saveGraphicsState();
                content.transform(Matrix.getScaleInstance(2, 2));
                content.drawForm((PDFormXObject) form);
                content.restoreGraphicsState();
            }
            PDImageXObject inForm = (PDImageXObject) form.getResources()
                    .getXObject(form.getResources().getXObjectNames().iterator().next());

            Map<COSStream, Double> measured = new ImageResolutionFinder().measure(document, SaveListener.NONE);

            assertEquals(600 / (200.0 / 72), measured.get((COSStream) inForm.getCOSObject()), 1.0);
        }
    }

    @Test
    void imageUsedTwiceIsMeasuredAtItsLowestResolution() throws Exception {
        Path source = directory.resolve("twice.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = JPEGFactory.createFromImage(document, TestFiles.syntheticScan(1200, 1200, 2, false), 0.9f);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, 400, 400);
                content.drawImage(image, 0, 400, 100, 100);
            }
            document.save(source.toFile());
        }

        assertEquals(1200 / (400.0 / 72), onlyResolution(source), 1.0);
    }

    @Test
    void imagesOnlySlightlyAboveTheTargetKeepTheirResolution() throws Exception {
        int pixels = (int) Math.round(160 * A4_WIDTH / 72);
        Path source = placed("close.pdf", pixels, pixels, A4_WIDTH, A4_WIDTH);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            assertEquals(pixels, TestFiles.imagesOf(document).get(0).getWidth());
        }
    }

    @Test
    void imagesWellAboveTheTargetAreReducedToTheTargetResolution() throws Exception {
        Path source = placed("half.pdf", 2000, 2800, A4_WIDTH / 2, A4_HEIGHT / 2);

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);

            double limitingPpi = 2800 / ((A4_HEIGHT / 2) / 72);
            long expected = Math.round(2000 * 150 / limitingPpi);
            assertEquals(expected, TestFiles.imagesOf(document).get(0).getWidth(), 1);
        }
    }

    @Test
    void aSmallDrawingOfAHugeImageIsReducedFarMoreThanAFullPage() throws Exception {
        Path full = placed("full.pdf", 2000, 2800, A4_WIDTH, A4_HEIGHT);
        Path small = placed("small.pdf", 2000, 2800, A4_WIDTH / 4, A4_HEIGHT / 4);

        assertTrue(widthAfter(small) < widthAfter(full) / 2);
    }

    @Test
    void imageListedButNeverDrawnIsNotTouched() throws Exception {
        Path source = directory.resolve("ghost.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject drawn = JPEGFactory.createFromImage(document, TestFiles.syntheticScan(600, 800, 1, false), 0.9f);
            PDImageXObject ghost = JPEGFactory.createFromImage(document, TestFiles.syntheticScan(1800, 2400, 2, false), 0.9f);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(drawn, 50, 50, 200, 266);
            }
            page.getResources().add(ghost);
            document.save(source.toFile());
        }

        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            CompressionStats stats = compressor.compress(document, CompressionLevel.STRONG, SaveListener.NONE);

            List<PDImageXObject> images = TestFiles.imagesOf(document);
            assertTrue(images.stream().anyMatch(image -> image.getWidth() == 1800));
            assertEquals(2, stats.images());
        }
    }

    private double onlyResolution(Path pdf) throws IOException, br.com.pdflocal.util.SaveCancelledException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            Map<COSStream, Double> measured = new ImageResolutionFinder().measure(document, SaveListener.NONE);
            assertEquals(1, measured.size());
            return measured.values().iterator().next();
        }
    }

    private int widthAfter(Path pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            compressor.compress(document, CompressionLevel.MEDIUM, SaveListener.NONE);
            return TestFiles.imagesOf(document).get(0).getWidth();
        }
    }

    private Path placed(String name, int pixelWidth, int pixelHeight, double drawWidth, double drawHeight)
            throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = JPEGFactory.createFromImage(document,
                    TestFiles.syntheticScan(pixelWidth, pixelHeight, 7, false), 0.9f);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, (float) drawWidth, (float) drawHeight);
            }
            document.save(file.toFile());
        }
        return file;
    }
}
