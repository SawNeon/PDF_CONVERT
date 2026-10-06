package br.com.pdflocal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageEditsTest {

    private final UUID source = UUID.randomUUID();
    private final List<PageItem> items = List.of(
            PageItem.pdfPage(source, 0), PageItem.pdfPage(source, 1),
            PageItem.pdfPage(source, 2), PageItem.pdfPage(source, 3));

    @Test
    void rotatesOnlyTheChosenItems() {
        List<PageItem> result = PageEdits.rotate(items, List.of(1, 3), 90);

        assertEquals(List.of(0, 90, 0, 90), rotations(result));
    }

    @Test
    void rotationAccumulatesAndWraps() {
        List<PageItem> once = PageEdits.rotate(items, List.of(0), -90);
        List<PageItem> twice = PageEdits.rotate(once, List.of(0), -90);
        List<PageItem> back = PageEdits.rotate(twice, List.of(0), 180);

        assertEquals(270, once.get(0).rotation());
        assertEquals(180, twice.get(0).rotation());
        assertEquals(0, back.get(0).rotation());
    }

    @Test
    void rotationKeepsItemIdentityAndUntouchedInstances() {
        List<PageItem> result = PageEdits.rotate(items, List.of(1), 90);

        assertEquals(items.get(1).id(), result.get(1).id());
        assertSame(items.get(0), result.get(0));
        assertNotSame(items.get(1), result.get(1));
    }

    @Test
    void doesNotChangeTheOriginalList() {
        PageEdits.rotate(items, List.of(0, 1), 90);
        PageEdits.delete(items, List.of(0, 1));

        assertEquals(List.of(0, 0, 0, 0), rotations(items));
        assertEquals(4, items.size());
    }

    @Test
    void deletesTheChosenItemsKeepingTheOthersInOrder() {
        List<PageItem> result = PageEdits.delete(items, List.of(2, 0));

        assertEquals(List.of(items.get(1), items.get(3)), result);
    }

    @Test
    void deletingNothingKeepsEverything() {
        assertEquals(items, PageEdits.delete(items, List.of()));
    }

    @Test
    void deletingEverythingLeavesAnEmptyList() {
        assertEquals(List.of(), PageEdits.delete(items, List.of(0, 1, 2, 3)));
    }

    @Test
    void rejectsIndexesOutOfRange() {
        assertThrows(IndexOutOfBoundsException.class, () -> PageEdits.rotate(items, List.of(4), 90));
        assertThrows(IndexOutOfBoundsException.class, () -> PageEdits.delete(items, List.of(-1)));
    }

    private static List<Integer> rotations(List<PageItem> list) {
        return list.stream().map(PageItem::rotation).toList();
    }
}
