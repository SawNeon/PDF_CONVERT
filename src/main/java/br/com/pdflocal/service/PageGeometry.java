package br.com.pdflocal.service;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

final class PageGeometry {

    record Placement(double x, double y, double angle) {
    }

    private static final int FULL_TURN = 360;
    private static final int QUARTER_TURN = 90;

    private PageGeometry() {
    }

    static PDRectangle visibleBox(PDPage page) {
        PDRectangle box = page.getCropBox();
        return box == null ? page.getMediaBox() : box;
    }

    static int rotation(PDPage page) {
        int rotation = Math.floorMod(page.getRotation(), FULL_TURN);
        return rotation % QUARTER_TURN == 0 ? rotation : 0;
    }

    static boolean swapsAxes(int rotation) {
        return rotation == 90 || rotation == 270;
    }

    static double displayWidth(PDRectangle box, int rotation) {
        return swapsAxes(rotation) ? box.getHeight() : box.getWidth();
    }

    static double displayHeight(PDRectangle box, int rotation) {
        return swapsAxes(rotation) ? box.getWidth() : box.getHeight();
    }

    static Placement place(PDRectangle box, int rotation, double displayX, double displayY) {
        double width = box.getWidth();
        double height = box.getHeight();
        double x;
        double y;
        double angle;
        switch (rotation) {
            case 90 -> {
                x = width - displayY;
                y = displayX;
                angle = Math.PI / 2;
            }
            case 180 -> {
                x = width - displayX;
                y = height - displayY;
                angle = Math.PI;
            }
            case 270 -> {
                x = displayY;
                y = height - displayX;
                angle = 3 * Math.PI / 2;
            }
            default -> {
                x = displayX;
                y = displayY;
                angle = 0;
            }
        }
        return new Placement(box.getLowerLeftX() + x, box.getLowerLeftY() + y, angle);
    }

    static PDRectangle toUserSpace(PDRectangle box, int rotation, double displayX0, double displayY0,
                                   double displayX1, double displayY1) {
        Placement a = place(box, rotation, displayX0, displayY0);
        Placement b = place(box, rotation, displayX1, displayY1);
        float left = (float) Math.min(a.x(), b.x());
        float bottom = (float) Math.min(a.y(), b.y());
        float right = (float) Math.max(a.x(), b.x());
        float top = (float) Math.max(a.y(), b.y());
        return new PDRectangle(left, bottom, right - left, top - bottom);
    }
}
