package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.RedactionArea;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class RedactStepTest {

    private static final RedactionArea TOP_LEFT_BLOCK = new RedactionArea(0.05, 0.05, 0.5, 0.2);

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void theRedactedTextIsGoneFromTheFileNotJustCovered() throws Exception {
        Path output = redact(TestFiles.pdf(directory, "paciente.pdf", "CPF-123-456-789"), 1, 0,
                Map.of(0, List.of(TOP_LEFT_BLOCK)));

        assertEquals("", TestFiles.pageTexts(output).get(0));
        String raw = new String(Files.readAllBytes(output), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("CPF-123-456-789"));
        BufferedImage image = TestFiles.render(output, 0);
        assertTrue(isBlack(image, (int) (0.1 * image.getWidth()), (int) (0.1 * image.getHeight())));
    }

    @Test
    void theAreaIsPaintedWhereTheUserMarkedItEvenOnRotatedPages() throws Exception {
        for (int rotation : new int[] {0, 90, 180, 270}) {
            Path output = redact(TestFiles.pdf(directory, "r" + rotation + ".pdf", "A1"), 1, rotation,
                    Map.of(0, List.of(new RedactionArea(0.6, 0.6, 0.9, 0.9))));

            BufferedImage image = TestFiles.render(output, 0);
            assertTrue(isBlack(image, (int) (0.75 * image.getWidth()), (int) (0.75 * image.getHeight())),
                    "inside, rotation " + rotation);
            assertFalse(isBlack(image, (int) (0.3 * image.getWidth()), (int) (0.3 * image.getHeight())),
                    "outside, rotation " + rotation);
        }
    }

    @Test
    void pagesWithoutMarksKeepTheirSelectableText() throws Exception {
        Path output = redact(TestFiles.pdf(directory, "three.pdf", "A1", "A2", "A3"), 3, 0,
                Map.of(1, List.of(TOP_LEFT_BLOCK)));

        assertEquals(List.of("A1", "", "A3"), TestFiles.pageTexts(output));
    }

    @Test
    void severalAreasOnTheSamePageAreAllPainted() throws Exception {
        Path output = redact(TestFiles.pdf(directory, "a.pdf", "A1"), 1, 0, Map.of(0, List.of(
                new RedactionArea(0.1, 0.5, 0.3, 0.6), new RedactionArea(0.6, 0.7, 0.9, 0.8))));

        BufferedImage image = TestFiles.render(output, 0);
        assertTrue(isBlack(image, (int) (0.2 * image.getWidth()), (int) (0.55 * image.getHeight())));
        assertTrue(isBlack(image, (int) (0.75 * image.getWidth()), (int) (0.75 * image.getHeight())));
        assertFalse(isBlack(image, (int) (0.45 * image.getWidth()), (int) (0.65 * image.getHeight())));
    }

    @Test
    void aMarkOnAPageThatDoesNotExistFailsInsteadOfBeingSkipped() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        Path output = directory.resolve("out.pdf");

        assertThrows(Exception.class, () -> composer.compose(List.of(PageItem.pdfPage(source.id(), 0)), registry,
                PageSize.ORIGINAL, List.of(new RedactStep(Map.of(5, List.of(TOP_LEFT_BLOCK)))), output,
                SaveListener.NONE));
        assertTrue(Files.notExists(output));
    }

    @Test
    void theOpenSourceKeepsItsText() throws Exception {
        Path source = TestFiles.pdf(directory, "a.pdf", "CPF-123");
        redact(source, 1, 0, Map.of(0, List.of(TOP_LEFT_BLOCK)));

        assertEquals(List.of("CPF-123"), TestFiles.pageTexts(source));
        try (PDDocument original = Loader.loadPDF(source.toFile())) {
            assertEquals(1, original.getNumberOfPages());
        }
    }

    @Test
    void invalidAreasAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RedactionArea(0.5, 0.5, 0.4, 0.9));
        assertThrows(IllegalArgumentException.class, () -> new RedactionArea(-0.1, 0, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RedactionArea(0, 0, 1.2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RedactStep(Map.of(-1, List.of(TOP_LEFT_BLOCK))));
    }

    private Path redact(Path source, int pages, int rotation, Map<Integer, List<RedactionArea>> areas)
            throws Exception {
        SourceDocument opened = registry.open(source);
        List<PageItem> items = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            PageItem item = PageItem.pdfPage(opened.id(), i);
            items.add(rotation == 0 ? item : item.rotatedBy(rotation));
        }
        Path output = directory.resolve("redacted-" + source.getFileName());
        composer.compose(items, registry, PageSize.ORIGINAL, List.of(new RedactStep(areas)), output,
                SaveListener.NONE);
        return output;
    }

    private static boolean isBlack(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y));
        return color.getRed() < 40 && color.getGreen() < 40 && color.getBlue() < 40;
    }
}
