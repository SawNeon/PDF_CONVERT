package br.com.pdflocal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UndoHistoryTest {

    private final UUID source = UUID.randomUUID();
    private final PageItem first = PageItem.pdfPage(source, 0);
    private final PageItem second = PageItem.pdfPage(source, 1);

    @Test
    void startsEmpty() {
        UndoHistory history = new UndoHistory();

        assertTrue(history.isEmpty());
        assertTrue(history.pop().isEmpty());
    }

    @Test
    void popsSnapshotsInReverseOrder() {
        UndoHistory history = new UndoHistory();
        history.push(List.of(first));
        history.push(List.of(first, second));

        assertEquals(List.of(first, second), history.pop().orElseThrow());
        assertEquals(List.of(first), history.pop().orElseThrow());
        assertTrue(history.pop().isEmpty());
    }

    @Test
    void keepsACopyOfWhatWasPushed() {
        UndoHistory history = new UndoHistory();
        List<PageItem> mutable = new ArrayList<>(List.of(first));

        history.push(mutable);
        mutable.add(second);

        assertEquals(List.of(first), history.pop().orElseThrow());
    }

    @Test
    void dropsTheOldestSnapshotsBeyondTheLimit() {
        UndoHistory history = new UndoHistory(2);
        history.push(List.of());
        history.push(List.of(first));
        history.push(List.of(first, second));

        assertEquals(List.of(first, second), history.pop().orElseThrow());
        assertEquals(List.of(first), history.pop().orElseThrow());
        assertTrue(history.pop().isEmpty());
    }

    @Test
    void clearForgetsEverything() {
        UndoHistory history = new UndoHistory();
        history.push(List.of(first));

        history.clear();

        assertTrue(history.isEmpty());
        assertTrue(history.referencedSources().isEmpty());
    }

    @Test
    void reportsThePdfSourcesStillReachableByUndo() {
        UUID other = UUID.randomUUID();
        UndoHistory history = new UndoHistory();
        history.push(List.of(first, PageItem.pdfPage(other, 0), PageItem.image(Path.of("photo.png"))));

        assertEquals(Set.of(source, other), history.referencedSources());
    }

    @Test
    void aSourceIsNoLongerReferencedOnceItsSnapshotsAreGone() {
        UUID other = UUID.randomUUID();
        UndoHistory history = new UndoHistory(1);
        history.push(List.of(PageItem.pdfPage(other, 0)));
        history.push(List.of(first));

        assertEquals(Set.of(source), history.referencedSources());
        assertFalse(history.referencedSources().contains(other));
    }

    @Test
    void rejectsANonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new UndoHistory(0));
    }
}
