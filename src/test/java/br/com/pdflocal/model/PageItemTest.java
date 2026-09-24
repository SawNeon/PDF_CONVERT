package br.com.pdflocal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageItemTest {

    private final UUID sourceId = UUID.randomUUID();

    @Test
    void pdfPageStartsWithoutRotation() {
        PageItem item = PageItem.pdfPage(sourceId, 3);

        assertFalse(item.isImage());
        assertEquals(3, item.pageIndex());
        assertEquals(0, item.rotation());
    }

    @Test
    void imageItemIsRecognized() {
        assertTrue(PageItem.image(Path.of("photo.jpg")).isImage());
    }

    @ParameterizedTest
    @ValueSource(ints = {-90, 45, 91, 360, 450})
    void rejectsInvalidRotation(int rotation) {
        assertThrows(IllegalArgumentException.class, () -> new PageItem(sourceId, 0, null, rotation));
    }

    @Test
    void rejectsItemThatIsNeitherPdfPageNorImage() {
        assertThrows(IllegalArgumentException.class, () -> new PageItem(null, 0, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageItem(sourceId, 0, Path.of("a.png"), 0));
        assertThrows(IllegalArgumentException.class, () -> new PageItem(sourceId, -1, null, 0));
    }

    @Test
    void rotationWrapsAroundInBothDirections() {
        PageItem item = PageItem.pdfPage(sourceId, 0);

        assertEquals(90, item.rotatedBy(90).rotation());
        assertEquals(0, item.rotatedBy(90).rotatedBy(90).rotatedBy(90).rotatedBy(90).rotation());
        assertEquals(270, item.rotatedBy(-90).rotation());
        assertEquals(180, item.rotatedBy(-90).rotatedBy(-90).rotation());
    }

    @Test
    void rotatingDoesNotChangeTheOriginalItem() {
        PageItem item = PageItem.pdfPage(sourceId, 0);

        PageItem rotated = item.rotatedBy(90);

        assertNotSame(item, rotated);
        assertEquals(0, item.rotation());
    }

    @Test
    void rejectsRotationThatIsNotMultipleOfNinety() {
        assertThrows(IllegalArgumentException.class, () -> PageItem.pdfPage(sourceId, 0).rotatedBy(45));
    }
}
