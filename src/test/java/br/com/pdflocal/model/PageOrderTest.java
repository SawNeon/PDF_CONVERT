package br.com.pdflocal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageOrderTest {

    private final List<String> pages = List.of("a", "b", "c", "d", "e");

    @Test
    void movesForwardToTheGapBeforeTheTargetPosition() {
        assertEquals(List.of("b", "c", "a", "d", "e"), PageOrder.move(pages, 0, 3));
    }

    @Test
    void movesBackward() {
        assertEquals(List.of("a", "d", "b", "c", "e"), PageOrder.move(pages, 3, 1));
    }

    @Test
    void movesToTheEnd() {
        assertEquals(List.of("b", "c", "d", "e", "a"), PageOrder.move(pages, 0, 5));
    }

    @Test
    void movesToTheStart() {
        assertEquals(List.of("e", "a", "b", "c", "d"), PageOrder.move(pages, 4, 0));
    }

    @Test
    void droppingRightBeforeOrRightAfterTheItemKeepsTheOrder() {
        assertEquals(pages, PageOrder.move(pages, 2, 2));
        assertEquals(pages, PageOrder.move(pages, 2, 3));
    }

    @Test
    void doesNotChangeTheOriginalList() {
        List<String> result = PageOrder.move(pages, 0, 5);

        assertNotSame(pages, result);
        assertEquals(List.of("a", "b", "c", "d", "e"), pages);
    }

    @Test
    void movesSeveralItemsAsABlockKeepingTheirRelativeOrder() {
        assertEquals(List.of("a", "c", "e", "b", "d"), PageOrder.moveMany(pages, List.of(1, 3), 5));
    }

    @Test
    void moveManyToTheEnd() {
        assertEquals(List.of("b", "d", "e", "a", "c"), PageOrder.moveMany(pages, List.of(0, 2), 5));
    }

    @Test
    void moveManyToTheStart() {
        assertEquals(List.of("c", "e", "a", "b", "d"), PageOrder.moveMany(pages, List.of(4, 2), 0));
    }

    @Test
    void moveManyIntoTheMiddleAdjustsForItemsBeforeTheGap() {
        assertEquals(List.of("b", "d", "a", "c", "e"), PageOrder.moveMany(pages, List.of(0, 2), 4));
        assertEquals(List.of("a", "d", "b", "c", "e"), PageOrder.moveMany(pages, List.of(3), 1));
    }

    @Test
    void moveManyIgnoresDuplicatedIndexesAndOrder() {
        assertEquals(PageOrder.moveMany(pages, List.of(0, 2), 5), PageOrder.moveMany(pages, List.of(2, 0, 2), 5));
    }

    @Test
    void moveManyWithNoIndexesKeepsTheOrder() {
        assertEquals(pages, PageOrder.moveMany(pages, List.of(), 3));
    }

    @Test
    void droppingABlockInsideItselfKeepsTheOrder() {
        assertEquals(pages, PageOrder.moveMany(pages, List.of(1, 2, 3), 2));
        assertEquals(pages, PageOrder.moveMany(pages, List.of(1, 2, 3), 1));
        assertEquals(pages, PageOrder.moveMany(pages, List.of(1, 2, 3), 4));
    }

    @Test
    void moveManyRejectsBadIndexes() {
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.moveMany(pages, List.of(5), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.moveMany(pages, List.of(0), 6));
    }

    @Test
    void rejectsIndexesOutOfRange() {
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.move(pages, 5, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.move(pages, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.move(pages, 0, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> PageOrder.move(pages, 0, -1));
    }
}
