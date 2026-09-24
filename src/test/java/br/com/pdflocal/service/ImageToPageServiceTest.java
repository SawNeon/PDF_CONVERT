package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.service.ImageToPageService.Layout;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageToPageServiceTest {

    private static final double DELTA = 0.01;
    private static final double A4_WIDTH = 595.28;
    private static final double A4_HEIGHT = 841.89;

    private final ImageToPageService service = new ImageToPageService();

    @TempDir
    Path directory;

    @Test
    void originalModeKeepsImageSize() {
        Layout layout = ImageToPageService.layout(200, 100, 1, PageSize.ORIGINAL);

        assertEquals(200, layout.pageWidth(), DELTA);
        assertEquals(100, layout.pageHeight(), DELTA);
        assertEquals(200, layout.width(), DELTA);
        assertEquals(100, layout.height(), DELTA);
    }

    @Test
    void originalModeSwapsSidesWhenOrientationRotatesQuarterTurn() {
        Layout layout = ImageToPageService.layout(200, 100, 6, PageSize.ORIGINAL);

        assertEquals(100, layout.pageWidth(), DELTA);
        assertEquals(200, layout.pageHeight(), DELTA);
    }

    @Test
    void originalModeLimitsHugeImagesKeepingProportion() {
        Layout layout = ImageToPageService.layout(30_000, 15_000, 1, PageSize.ORIGINAL);

        assertEquals(14_400, layout.pageWidth(), DELTA);
        assertEquals(7_200, layout.pageHeight(), DELTA);
    }

    @Test
    void a4UsesPortraitForTallImages() {
        Layout layout = ImageToPageService.layout(100, 200, 1, PageSize.A4);

        assertEquals(A4_WIDTH, layout.pageWidth(), DELTA);
        assertEquals(A4_HEIGHT, layout.pageHeight(), DELTA);
        assertFitsInsideMarginsAndCentered(layout, 0.5);
    }

    @Test
    void a4UsesLandscapeForWideImages() {
        Layout layout = ImageToPageService.layout(200, 100, 1, PageSize.A4);

        assertEquals(A4_HEIGHT, layout.pageWidth(), DELTA);
        assertEquals(A4_WIDTH, layout.pageHeight(), DELTA);
        assertFitsInsideMarginsAndCentered(layout, 2.0);
    }

    @Test
    void a4UsesPortraitForSquareImages() {
        Layout layout = ImageToPageService.layout(100, 100, 1, PageSize.A4);

        assertEquals(A4_WIDTH, layout.pageWidth(), DELTA);
        assertEquals(A4_HEIGHT, layout.pageHeight(), DELTA);
        assertFitsInsideMarginsAndCentered(layout, 1.0);
    }

    @Test
    void a4ChoosesOrientationFromTheCorrectedImage() {
        Layout layout = ImageToPageService.layout(200, 100, 6, PageSize.A4);

        assertEquals(A4_WIDTH, layout.pageWidth(), DELTA);
        assertEquals(A4_HEIGHT, layout.pageHeight(), DELTA);
        assertFitsInsideMarginsAndCentered(layout, 0.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "png", "gif", "bmp", "tiff"})
    void everySupportedFormatBecomesAPage(String format) throws Exception {
        Path image = TestFiles.image(directory, "image." + format, format, 40, 20);

        try (PDDocument document = new PDDocument()) {
            PDPage page = service.addPage(document, image, PageSize.ORIGINAL);

            assertEquals(1, document.getNumberOfPages());
            assertEquals(40, page.getMediaBox().getWidth(), DELTA);
            assertEquals(20, page.getMediaBox().getHeight(), DELTA);
        }
    }

    @Test
    void jpegIsEmbeddedWithoutRecompression() throws Exception {
        Path image = TestFiles.image(directory, "photo.jpg", "jpg", 64, 48);

        try (PDDocument document = new PDDocument()) {
            PDPage page = service.addPage(document, image, PageSize.ORIGINAL);

            COSName name = page.getResources().getXObjectNames().iterator().next();
            PDImageXObject embedded = (PDImageXObject) page.getResources().getXObject(name);
            byte[] embeddedBytes = embedded.getCOSObject().createRawInputStream().readAllBytes();

            assertEquals("jpg", embedded.getSuffix());
            assertArrayEquals(Files.readAllBytes(image), embeddedBytes);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, RED,    GREEN,  BLUE,   YELLOW",
        "2, GREEN,  RED,    YELLOW, BLUE",
        "3, YELLOW, BLUE,   GREEN,  RED",
        "4, BLUE,   YELLOW, RED,    GREEN",
        "5, RED,    BLUE,   GREEN,  YELLOW",
        "6, BLUE,   RED,    YELLOW, GREEN",
        "7, YELLOW, GREEN,  BLUE,   RED",
        "8, GREEN,  YELLOW, RED,    BLUE"
    })
    void exifOrientationIsAppliedToTheRenderedPage(
            int orientation, String topLeft, String topRight, String bottomLeft, String bottomRight)
            throws Exception {
        Path image = TestFiles.jpegWithOrientation(directory, "photo.jpg", 80, 40, orientation);
        Path output = directory.resolve("out.pdf");

        try (PDDocument document = new PDDocument()) {
            service.addPage(document, image, PageSize.ORIGINAL);
            document.save(output.toFile());
        }
        BufferedImage rendered = TestFiles.render(output, 0);

        boolean quarterTurn = orientation >= 5;
        assertEquals(quarterTurn ? 40 : 80, rendered.getWidth());
        assertEquals(quarterTurn ? 80 : 40, rendered.getHeight());
        int w = rendered.getWidth();
        int h = rendered.getHeight();
        assertEquals(color(topLeft), TestFiles.dominantColor(rendered.getRGB(w / 4, h / 4)), "top left");
        assertEquals(color(topRight), TestFiles.dominantColor(rendered.getRGB(3 * w / 4, h / 4)), "top right");
        assertEquals(color(bottomLeft), TestFiles.dominantColor(rendered.getRGB(w / 4, 3 * h / 4)), "bottom left");
        assertEquals(color(bottomRight), TestFiles.dominantColor(rendered.getRGB(3 * w / 4, 3 * h / 4)),
                "bottom right");
    }

    @Test
    void textFileWithImageExtensionIsRejected() throws Exception {
        Path fake = TestFiles.fakeFile(directory, "fake.png", "plain text");

        try (PDDocument document = new PDDocument()) {
            assertThrows(UnsupportedFileException.class, () -> service.addPage(document, fake, PageSize.ORIGINAL));
            assertEquals(0, document.getNumberOfPages());
        }
    }

    @Test
    void pdfIsNotAcceptedAsImage() throws Exception {
        Path pdf = TestFiles.pdf(directory, "photo.png", "text");

        try (PDDocument document = new PDDocument()) {
            assertThrows(UnsupportedFileException.class, () -> service.addPage(document, pdf, PageSize.ORIGINAL));
        }
    }

    @Test
    void truncatedImageIsReportedAsUnreadable() throws Exception {
        Path valid = TestFiles.image(directory, "valid.png", "png", 50, 50);
        byte[] bytes = Files.readAllBytes(valid);
        Path truncated = directory.resolve("truncated.png");
        Files.write(truncated, java.util.Arrays.copyOf(bytes, 40));

        try (PDDocument document = new PDDocument()) {
            assertThrows(CorruptFileException.class, () -> service.addPage(document, truncated, PageSize.ORIGINAL));
        }
    }

    private static void assertFitsInsideMarginsAndCentered(Layout layout, double aspectRatio) {
        double margin = ImageToPageService.A4_MARGIN;
        assertTrue(layout.x() >= margin - DELTA, "left margin");
        assertTrue(layout.y() >= margin - DELTA, "bottom margin");
        assertTrue(layout.x() + layout.width() <= layout.pageWidth() - margin + DELTA, "right margin");
        assertTrue(layout.y() + layout.height() <= layout.pageHeight() - margin + DELTA, "top margin");
        assertEquals(aspectRatio, layout.width() / layout.height(), 0.001);
        assertEquals(layout.pageWidth() / 2, layout.x() + layout.width() / 2, DELTA);
        assertEquals(layout.pageHeight() / 2, layout.y() + layout.height() / 2, DELTA);
    }

    private static Color color(String name) {
        return switch (name) {
            case "RED" -> TestFiles.RED;
            case "GREEN" -> TestFiles.GREEN;
            case "BLUE" -> TestFiles.BLUE;
            default -> TestFiles.YELLOW;
        };
    }
}
