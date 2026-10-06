package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GridSelectionTest {

    private final List<UUID> order = Stream.generate(UUID::randomUUID).limit(6).toList();
    private final GridSelection selection = new GridSelection();

    @Test
    void startsEmpty() {
        assertTrue(selection.isEmpty());
        assertEquals(List.of(), selection.indices(order));
    }

    @Test
    void plainClickSelectsOnlyThatCard() {
        selection.click(order, 1, false, false);
        selection.click(order, 3, false, false);

        assertEquals(List.of(3), selection.indices(order));
    }

    @Test
    void ctrlClickTogglesCards() {
        selection.click(order, 1, false, false);
        selection.click(order, 4, true, false);
        selection.click(order, 2, true, false);

        assertEquals(List.of(1, 2, 4), selection.indices(order));

        selection.click(order, 4, true, false);

        assertEquals(List.of(1, 2), selection.indices(order));
    }

    @Test
    void shiftClickSelectsTheRangeFromTheAnchor() {
        selection.click(order, 1, false, false);
        selection.click(order, 4, false, true);

        assertEquals(List.of(1, 2, 3, 4), selection.indices(order));
    }

    @Test
    void shiftClickWorksBackwardsAndKeepsTheAnchor() {
        selection.click(order, 3, false, false);
        selection.click(order, 1, false, true);
        assertEquals(List.of(1, 2, 3), selection.indices(order));

        selection.click(order, 5, false, true);
        assertEquals(List.of(3, 4, 5), selection.indices(order));
    }

    @Test
    void ctrlShiftClickAddsTheRangeToTheCurrentSelection() {
        selection.click(order, 0, false, false);
        selection.click(order, 3, true, false);
        selection.click(order, 5, true, true);

        assertEquals(List.of(0, 3, 4, 5), selection.indices(order));
    }

    @Test
    void shiftClickWithoutAnchorActsAsPlainClick() {
        selection.click(order, 3, false, true);

        assertEquals(List.of(3), selection.indices(order));
    }

    @Test
    void selectAllAndClear() {
        selection.selectAll(order);
        assertEquals(6, selection.size());

        selection.clear();
        assertTrue(selection.isEmpty());
    }

    @Test
    void retainDropsCardsThatNoLongerExist() {
        selection.selectAll(order);

        selection.retain(order.subList(0, 3));

        assertEquals(List.of(0, 1, 2), selection.indices(order));
        assertTrue(selection.isSelected(order.get(0)));
        assertFalse(selection.isSelected(order.get(5)));
    }

    @Test
    void retainForgetsTheAnchorWhenItDisappears() {
        selection.click(order, 5, false, false);
        selection.retain(order.subList(0, 3));

        selection.click(order, 1, false, true);

        assertEquals(List.of(1), selection.indices(order));
    }

    @Test
    void setReplacesTheSelection() {
        selection.click(order, 0, false, false);

        selection.set(Set.of(order.get(2), order.get(4)));

        assertEquals(List.of(2, 4), selection.indices(order));
    }

    @Test
    void selectOnlyReplacesTheSelectionAndSetsTheAnchor() {
        selection.selectAll(order);

        selection.selectOnly(order.get(2));
        selection.click(order, 4, false, true);

        assertEquals(List.of(2, 3, 4), selection.indices(order));
    }
}
