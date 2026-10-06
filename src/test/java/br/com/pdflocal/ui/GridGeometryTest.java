package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.pdflocal.ui.GridGeometry.Insertion;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class GridGeometryTest {

    private static final double GAP = 10;
    private static final double DELTA = 0.001;

    private final List<Rectangle2D> cells = threeColumnGrid(5);

    @Test
    void emptyGridInsertsAtTheStart() {
        assertEquals(0, GridGeometry.insertionAt(List.of(), 50, 50, GAP).index());
    }

    @Test
    void leftHalfOfACellInsertsBeforeIt() {
        Insertion insertion = GridGeometry.insertionAt(cells, 10, 50, GAP);

        assertEquals(0, insertion.index());
        assertEquals(-5, insertion.x(), DELTA);
        assertEquals(0, insertion.top(), DELTA);
        assertEquals(100, insertion.height(), DELTA);
    }

    @Test
    void rightHalfOfACellInsertsAfterIt() {
        Insertion insertion = GridGeometry.insertionAt(cells, 90, 50, GAP);

        assertEquals(1, insertion.index());
        assertEquals(105, insertion.x(), DELTA);
    }

    @Test
    void pointerInTheGapBetweenCellsPicksTheNearerSide() {
        assertEquals(1, GridGeometry.insertionAt(cells, 104, 50, GAP).index());
        assertEquals(1, GridGeometry.insertionAt(cells, 107, 50, GAP).index());
    }

    @Test
    void pointerPastTheEndOfARowInsertsAfterTheLastCellOfThatRow() {
        Insertion insertion = GridGeometry.insertionAt(cells, 900, 50, GAP);

        assertEquals(3, insertion.index());
        assertEquals(325, insertion.x(), DELTA);
    }

    @Test
    void secondRowIsRecognizedByTheVerticalPosition() {
        assertEquals(3, GridGeometry.insertionAt(cells, 10, 150, GAP).index());
        assertEquals(4, GridGeometry.insertionAt(cells, 130, 150, GAP).index());
        assertEquals(5, GridGeometry.insertionAt(cells, 230, 150, GAP).index());
    }

    @Test
    void pointerBelowEverythingInsertsInTheLastRow() {
        assertEquals(3, GridGeometry.insertionAt(cells, 10, 900, GAP).index());
        assertEquals(5, GridGeometry.insertionAt(cells, 300, 900, GAP).index());
    }

    @Test
    void pointerAboveEverythingInsertsInTheFirstRow() {
        assertEquals(0, GridGeometry.insertionAt(cells, 10, -50, GAP).index());
    }

    private static List<Rectangle2D> threeColumnGrid(int count) {
        List<Rectangle2D> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double x = (i % 3) * 110;
            double y = (i / 3) * 110;
            result.add(new Rectangle2D(x, y, 100, 100));
        }
        return result;
    }
}
