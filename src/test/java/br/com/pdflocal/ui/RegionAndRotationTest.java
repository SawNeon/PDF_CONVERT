package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class RegionAndRotationTest {

    @Test
    void dragInAnyDirectionGivesTheSameOrderedRegion() {
        NormalizedRegion forward = NormalizedRegion.fromDrag(20, 40, 120, 140, 200, 400);
        NormalizedRegion backward = NormalizedRegion.fromDrag(120, 140, 20, 40, 200, 400);

        assertEquals(forward, backward);
        assertEquals(0.1, forward.left(), 1e-9);
        assertEquals(0.1, forward.top(), 1e-9);
        assertEquals(0.6, forward.right(), 1e-9);
        assertEquals(0.35, forward.bottom(), 1e-9);
    }

    @Test
    void dragOutsideTheImageIsClamped() {
        NormalizedRegion region = NormalizedRegion.fromDrag(-30, -10, 500, 900, 200, 400);

        assertEquals(0, region.left());
        assertEquals(0, region.top());
        assertEquals(1, region.right());
        assertEquals(1, region.bottom());
    }

    @Test
    void tinyDragsAreIgnored() {
        assertNull(NormalizedRegion.fromDrag(10, 10, 12, 80, 200, 400));
        assertNull(NormalizedRegion.fromDrag(10, 10, 80, 11, 200, 400));
        assertNull(NormalizedRegion.fromDrag(0, 0, 50, 50, 0, 0));
    }

    @Test
    void quarterTurnsMovePixelsClockwise() {
        int[] pixels = {1, 2, 3, 4, 5, 6};

        PixelRotation.Rotated right = PixelRotation.rotate(pixels, 3, 2, 90);
        PixelRotation.Rotated half = PixelRotation.rotate(pixels, 3, 2, 180);
        PixelRotation.Rotated left = PixelRotation.rotate(pixels, 3, 2, 270);

        assertEquals(2, right.width());
        assertEquals(3, right.height());
        assertArrayEquals(new int[] {4, 1, 5, 2, 6, 3}, right.pixels());
        assertArrayEquals(new int[] {6, 5, 4, 3, 2, 1}, half.pixels());
        assertArrayEquals(new int[] {3, 6, 2, 5, 1, 4}, left.pixels());
    }

    @Test
    void noRotationReturnsTheSamePixels() {
        int[] pixels = {1, 2, 3, 4};

        assertSame(pixels, PixelRotation.rotate(pixels, 2, 2, 0).pixels());
        assertSame(pixels, PixelRotation.rotate(pixels, 2, 2, 360).pixels());
    }
}
