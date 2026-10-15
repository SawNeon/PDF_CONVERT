package br.com.pdflocal.model;

public record RedactionArea(double left, double top, double right, double bottom) {

    public static final double MIN_SIDE = 0.004;

    public RedactionArea {
        boolean inRange = left >= 0 && top >= 0 && right <= 1 && bottom <= 1;
        if (!inRange || right - left < MIN_SIDE || bottom - top < MIN_SIDE) {
            throw new IllegalArgumentException("Invalid redaction area");
        }
    }
}
