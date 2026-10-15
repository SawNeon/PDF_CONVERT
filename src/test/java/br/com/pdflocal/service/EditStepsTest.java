package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.CropOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageNumberOptions;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.model.WatermarkOptions;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class EditStepsTest {

    private static final int[] ROTATIONS = {0, 90, 180, 270};
    private static final int DARK = 140;

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void pageNumberAppearsAtTheVisibleBottomCenterForEveryRotation() throws Exception {
        for (int rotation : ROTATIONS) {
            Path output = run(blankPdf("blank" + rotation + ".pdf", 1), rotation,
                    new PageNumbersStep(numbers(PageNumberOptions.Position.BOTTOM_CENTER, "{n}", 1, false)));

            BufferedImage image = TestFiles.render(output, 0);
            int w = image.getWidth();
            int h = image.getHeight();
            assertTrue(hasInk(image, w / 2 - 45, h - 56, w / 2 + 45, h - 14), "bottom, rotation " + rotation);
            assertFalse(hasInk(image, w / 2 - 45, 14, w / 2 + 45, 56), "top, rotation " + rotation);
        }
    }

    @Test
    void pageNumberHonoursTheCornerPositions() throws Exception {
        for (int rotation : new int[] {0, 270}) {
            Path topLeft = run(blankPdf("tl" + rotation + ".pdf", 1), rotation,
                    new PageNumbersStep(numbers(PageNumberOptions.Position.TOP_LEFT, "{n}", 1, false)));
            BufferedImage image = TestFiles.render(topLeft, 0);
            assertTrue(hasInk(image, 14, 14, 140, 56), "top-left, rotation " + rotation);
            assertFalse(hasInk(image, image.getWidth() - 140, image.getHeight() - 56, image.getWidth() - 14,
                    image.getHeight() - 14), "bottom-right empty, rotation " + rotation);

            Path bottomRight = run(blankPdf("br" + rotation + ".pdf", 1), rotation,
                    new PageNumbersStep(numbers(PageNumberOptions.Position.BOTTOM_RIGHT, "{n}", 1, false)));
            BufferedImage other = TestFiles.render(bottomRight, 0);
            assertTrue(hasInk(other, other.getWidth() - 140, other.getHeight() - 56, other.getWidth() - 14,
                    other.getHeight() - 14), "bottom-right, rotation " + rotation);
        }
    }

    @Test
    void numberingUsesTheStartNumberTotalAndSkipsTheFirstPage() throws Exception {
        Path source = blankPdf("three.pdf", 3);
        SourceDocument opened = registry.open(source);
        Path output = directory.resolve("numbered.pdf");

        composer.compose(pages(opened, 3), registry, PageSize.ORIGINAL,
                List.of(new PageNumbersStep(numbers(PageNumberOptions.Position.BOTTOM_CENTER,
                        "Página {n} de {total}", 5, true))), output, SaveListener.NONE);

        List<String> texts = TestFiles.pageTexts(output);
        assertEquals("", texts.get(0));
        assertEquals("Página 6 de 7", texts.get(1));
        assertEquals("Página 7 de 7", texts.get(2));
    }

    @Test
    void stampingDoesNotModifyTheOpenSourceDocument() throws Exception {
        SourceDocument opened = registry.open(TestFiles.pdf(directory, "source.pdf", "A1"));
        PDPage sourcePage = registry.document(opened.id()).getPage(0);
        int fontsBefore = fontCount(sourcePage);

        composer.compose(pages(opened, 1), registry, PageSize.ORIGINAL,
                List.of(new PageNumbersStep(numbers(PageNumberOptions.Position.BOTTOM_CENTER, "{n}", 1, false)),
                        new WatermarkStep(WatermarkOptions.ofText("CONFIDENCIAL", 0.3, true))),
                directory.resolve("out.pdf"), SaveListener.NONE);

        assertEquals(1, fontsBefore);
        assertEquals(fontsBefore, fontCount(registry.document(opened.id()).getPage(0)));
    }

    @Test
    void textWatermarkIsCenteredAndSelectableForEveryRotation() throws Exception {
        for (int rotation : ROTATIONS) {
            for (boolean diagonal : new boolean[] {true, false}) {
                Path output = run(blankPdf("wm" + rotation + diagonal + ".pdf", 1), rotation,
                        new WatermarkStep(WatermarkOptions.ofText("CONFIDENCIAL", 1.0, diagonal)));

                BufferedImage image = TestFiles.render(output, 0);
                int cx = image.getWidth() / 2;
                int cy = image.getHeight() / 2;
                assertTrue(hasInk(image, cx - 40, cy - 40, cx + 40, cy + 40),
                        "center, rotation " + rotation + " diagonal " + diagonal);
                assertEquals("CONFIDENCIAL", TestFiles.pageTexts(output).get(0).replaceAll("\\s+", ""));
            }
        }
    }

    @Test
    void watermarkOpacityMakesItLighter() throws Exception {
        Path strong = run(blankPdf("strong.pdf", 1), 0,
                new WatermarkStep(WatermarkOptions.ofText("CONFIDENCIAL", 1.0, false)));
        Path faint = run(blankPdf("faint.pdf", 1), 0,
                new WatermarkStep(WatermarkOptions.ofText("CONFIDENCIAL", 0.2, false)));

        assertTrue(darkest(TestFiles.render(strong, 0)) < DARK);
        assertTrue(darkest(TestFiles.render(faint, 0)) > 180);
    }

    @Test
    void imageWatermarkIsDrawnAtTheCenter() throws Exception {
        Path logo = TestFiles.image(directory, "logo.png", "png", 120, 120);

        Path output = run(blankPdf("img.pdf", 1), 90,
                new WatermarkStep(WatermarkOptions.ofImage(logo, 1.0, false)));

        BufferedImage image = TestFiles.render(output, 0);
        assertTrue(hasColor(image, image.getWidth() / 2 - 30, image.getHeight() / 2 - 30,
                image.getWidth() / 2 + 30, image.getHeight() / 2 + 30));
    }

    @Test
    void onlyEncodableTextIsAcceptedForStamps() {
        assertTrue(StandardText.canEncode("Cópia controlada - ação nº 1"));
        assertFalse(StandardText.canEncode("機密"));
    }

    @Test
    void hidingTheOutsideSetsTheCropBoxButKeepsTheText() throws Exception {
        Path output = run(TestFiles.pdf(directory, "text.pdf", "A1"), 0,
                new CropStep(new CropOptions(0.1, 0.2, 0.6, 0.7, CropOptions.Mode.HIDE_OUTSIDE)));

        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            PDRectangle box = saved.getPage(0).getCropBox();
            assertEquals(0.5 * 612, box.getWidth(), 1.0);
            assertEquals(0.5 * 792, box.getHeight(), 1.0);
        }
        assertEquals("A1", TestFiles.pageTexts(output).get(0));
    }

    @Test
    void hidingTheOutsideOfARotatedPageFollowsWhatTheUserSees() throws Exception {
        Path output = run(TestFiles.pdf(directory, "text.pdf", "A1"), 90,
                new CropStep(new CropOptions(0, 0, 0.5, 0.25, CropOptions.Mode.HIDE_OUTSIDE)));

        BufferedImage image = TestFiles.render(output, 0);
        assertEquals(0.5 * 792, image.getWidth(), 2.0);
        assertEquals(0.25 * 612, image.getHeight(), 2.0);
    }

    @Test
    void removingTheOutsideDeletesTheCutContentFromTheFile() throws Exception {
        Path output = run(TestFiles.pdf(directory, "secret.pdf", "SEGREDO"), 0,
                new CropStep(new CropOptions(0, 0.5, 1, 1, CropOptions.Mode.REMOVE_OUTSIDE)));

        assertEquals("", TestFiles.pageTexts(output).get(0));
        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            assertEquals(0.5 * 792, saved.getPage(0).getMediaBox().getHeight(), 1.0);
            assertEquals(612, saved.getPage(0).getMediaBox().getWidth(), 1.0);
        }
        assertFalse(new String(java.nio.file.Files.readAllBytes(output),
                java.nio.charset.StandardCharsets.ISO_8859_1).contains("SEGREDO"));
    }

    @Test
    void removingTheOutsideKeepsTheVisibleArea() throws Exception {
        Path output = run(TestFiles.pdf(directory, "text.pdf", "A1"), 0,
                new CropStep(new CropOptions(0, 0, 1, 0.3, CropOptions.Mode.REMOVE_OUTSIDE)));

        BufferedImage image = TestFiles.render(output, 0);
        assertEquals(0.3 * 792, image.getHeight(), 2.0);
        assertTrue(hasInk(image, 70, 70, 120, 95));
    }

    @Test
    void rasterizingCapsTheResolutionOfHugePages() {
        assertEquals(200, PageRasterizer.effectiveDpi(595, 842, 200), 0.001);
        double capped = PageRasterizer.effectiveDpi(14_400, 14_400, 200);
        double pixels = Math.pow(14_400 / 72.0 * capped, 2);
        assertTrue(pixels <= PageRasterizer.MAX_PIXELS * 1.001);
    }

    @Test
    void geometryMapsDisplayPointsBackToUserSpace() {
        PDRectangle box = new PDRectangle(10, 20, 600, 800);

        PageGeometry.Placement r0 = PageGeometry.place(box, 0, 100, 50);
        PageGeometry.Placement r90 = PageGeometry.place(box, 90, 100, 50);
        PageGeometry.Placement r180 = PageGeometry.place(box, 180, 100, 50);
        PageGeometry.Placement r270 = PageGeometry.place(box, 270, 100, 50);

        assertEquals(110, r0.x(), 1e-6);
        assertEquals(70, r0.y(), 1e-6);
        assertEquals(10 + 600 - 50, r90.x(), 1e-6);
        assertEquals(20 + 100, r90.y(), 1e-6);
        assertEquals(10 + 600 - 100, r180.x(), 1e-6);
        assertEquals(20 + 800 - 50, r180.y(), 1e-6);
        assertEquals(10 + 50, r270.x(), 1e-6);
        assertEquals(20 + 800 - 100, r270.y(), 1e-6);
    }

    private Path run(Path source, int rotation, OutputStep step) throws Exception {
        SourceDocument opened = registry.open(source);
        PageItem item = PageItem.pdfPage(opened.id(), 0);
        List<PageItem> items = new ArrayList<>();
        items.add(rotation == 0 ? item : item.rotatedBy(rotation));
        Path output = directory.resolve("out-" + source.getFileName());
        composer.compose(items, registry, PageSize.ORIGINAL, List.of(step), output, SaveListener.NONE);
        return output;
    }

    private static List<PageItem> pages(SourceDocument source, int count) {
        List<PageItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(PageItem.pdfPage(source.id(), i));
        }
        return items;
    }

    private static PageNumberOptions numbers(PageNumberOptions.Position position, String pattern, int start,
                                             boolean skipFirst) {
        return new PageNumberOptions(position, pattern, start, skipFirst, 12);
    }

    private static int fontCount(PDPage page) {
        int count = 0;
        for (var ignored : page.getResources().getFontNames()) {
            count++;
        }
        return count;
    }

    private Path blankPdf(String name, int pages) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(PDRectangle.LETTER));
            }
            document.save(file.toFile());
        }
        return file;
    }

    private static boolean hasInk(BufferedImage image, int x0, int y0, int x1, int y1) {
        return darkestIn(image, x0, y0, x1, y1) < DARK;
    }

    private static int darkest(BufferedImage image) {
        return darkestIn(image, 0, 0, image.getWidth(), image.getHeight());
    }

    private static int darkestIn(BufferedImage image, int x0, int y0, int x1, int y1) {
        int darkest = 255;
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                Color color = new Color(image.getRGB(x, y));
                darkest = Math.min(darkest, (color.getRed() + color.getGreen() + color.getBlue()) / 3);
            }
        }
        return darkest;
    }

    private static boolean hasColor(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                Color color = new Color(image.getRGB(x, y));
                if (Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue())) < 200) {
                    return true;
                }
            }
        }
        return false;
    }
}
