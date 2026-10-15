package br.com.pdflocal.ui;

record NormalizedRegion(double left, double top, double right, double bottom) {

    static final double MIN_PIXELS = 6;

    static NormalizedRegion fromDrag(double startX, double startY, double endX, double endY,
                                     double width, double height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        double x0 = clamp(Math.min(startX, endX), width);
        double x1 = clamp(Math.max(startX, endX), width);
        double y0 = clamp(Math.min(startY, endY), height);
        double y1 = clamp(Math.max(startY, endY), height);
        if (x1 - x0 < MIN_PIXELS || y1 - y0 < MIN_PIXELS) {
            return null;
        }
        return new NormalizedRegion(x0 / width, y0 / height, x1 / width, y1 / height);
    }

    private static double clamp(double value, double limit) {
        return Math.max(0, Math.min(limit, value));
    }
}
