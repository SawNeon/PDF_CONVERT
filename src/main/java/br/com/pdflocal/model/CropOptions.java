package br.com.pdflocal.model;

import java.util.Objects;

public record CropOptions(double left, double top, double right, double bottom, Mode mode) {

    public enum Mode {
        HIDE_OUTSIDE,
        REMOVE_OUTSIDE
    }

    public static final double MIN_SIDE = 0.05;

    public CropOptions {
        Objects.requireNonNull(mode, "mode");
        boolean inRange = left >= 0 && top >= 0 && right <= 1 && bottom <= 1;
        if (!inRange || right - left < MIN_SIDE || bottom - top < MIN_SIDE) {
            throw new IllegalArgumentException("Invalid crop area");
        }
    }
}
