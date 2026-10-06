package br.com.pdflocal.ui;

import java.util.List;
import javafx.geometry.Rectangle2D;

public final class GridGeometry {

    public record Insertion(int index, double x, double top, double height) {
    }

    private GridGeometry() {
    }

    public static Insertion insertionAt(List<Rectangle2D> cells, double x, double y, double gap) {
        if (cells.isEmpty()) {
            return new Insertion(0, 0, 0, 0);
        }

        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            double distance = squaredDistance(cells.get(i), x, y);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }

        Rectangle2D cell = cells.get(nearest);
        boolean before = x < (cell.getMinX() + cell.getMaxX()) / 2;
        double lineX = before ? cell.getMinX() - gap / 2 : cell.getMaxX() + gap / 2;
        return new Insertion(before ? nearest : nearest + 1, lineX, cell.getMinY(), cell.getHeight());
    }

    private static double squaredDistance(Rectangle2D cell, double x, double y) {
        double dx = Math.max(Math.max(cell.getMinX() - x, 0), x - cell.getMaxX());
        double dy = Math.max(Math.max(cell.getMinY() - y, 0), y - cell.getMaxY());
        return dx * dx + dy * dy;
    }
}
