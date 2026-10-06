package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZoomModelTest {

    private static final double DELTA = 0.0001;

    private final ZoomModel zoom = new ZoomModel();

    @Test
    void startsInFitMode() {
        assertTrue(zoom.isFit());
    }

    @Test
    void fitScaleUsesTheTighterAxis() {
        assertEquals(0.5, ZoomModel.fitScale(500, 2000, 1000, 1000), DELTA);
        assertEquals(0.5, ZoomModel.fitScale(2000, 500, 1000, 1000), DELTA);
    }

    @Test
    void fitScaleMayEnlargeSmallImages() {
        assertEquals(2.0, ZoomModel.fitScale(2000, 2000, 1000, 1000), DELTA);
    }

    @Test
    void fitScaleIsSafeWithDegenerateSizes() {
        assertEquals(1.0, ZoomModel.fitScale(0, 100, 100, 100), DELTA);
        assertEquals(1.0, ZoomModel.fitScale(100, 100, 0, 100), DELTA);
    }

    @Test
    void scaleFollowsTheViewportInFitMode() {
        assertEquals(0.5, zoom.scale(500, 500, 1000, 800), DELTA);
        assertEquals(0.25, zoom.scale(250, 500, 1000, 800), DELTA);
    }

    @Test
    void zoomInGoesToTheNextStepAboveTheCurrentScale() {
        zoom.zoomIn(0.63);

        assertFalse(zoom.isFit());
        assertEquals(0.67, zoom.scale(100, 100, 1000, 1000), DELTA);

        zoom.zoomIn(0.67);
        assertEquals(0.75, zoom.scale(100, 100, 1000, 1000), DELTA);
    }

    @Test
    void zoomOutGoesToTheNextStepBelowTheCurrentScale() {
        zoom.zoomOut(0.63);

        assertEquals(0.5, zoom.scale(100, 100, 1000, 1000), DELTA);

        zoom.zoomOut(0.5);
        assertEquals(0.33, zoom.scale(100, 100, 1000, 1000), DELTA);
    }

    @Test
    void zoomIsClampedToTheLimits() {
        zoom.zoomIn(4.0);
        assertEquals(4.0, zoom.scale(100, 100, 1000, 1000), DELTA);

        zoom.zoomOut(0.25);
        assertEquals(0.25, zoom.scale(100, 100, 1000, 1000), DELTA);

        zoom.zoomIn(10);
        assertEquals(4.0, zoom.scale(100, 100, 1000, 1000), DELTA);
    }

    @Test
    void zoomingFromAnExactStepDoesNotStickToIt() {
        zoom.zoomIn(1.0);
        assertEquals(1.25, zoom.scale(1, 1, 1, 1), DELTA);

        zoom.zoomOut(1.0);
        assertEquals(0.75, zoom.scale(1, 1, 1, 1), DELTA);
    }

    @Test
    void actualSizeIsOneHundredPercent() {
        zoom.zoomIn(0.5);

        zoom.actualSize();

        assertFalse(zoom.isFit());
        assertEquals(1.0, zoom.scale(10, 10, 1000, 1000), DELTA);
    }

    @Test
    void fitReturnsToAutomaticMode() {
        zoom.actualSize();

        zoom.fit();

        assertTrue(zoom.isFit());
        assertEquals(0.5, zoom.scale(500, 500, 1000, 800), DELTA);
    }

    @Test
    void percentRoundsToWholeNumbers() {
        assertEquals(67, ZoomModel.percent(0.666));
        assertEquals(100, ZoomModel.percent(1.0));
        assertEquals(400, ZoomModel.percent(4.0));
    }
}
