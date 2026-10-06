package br.com.pdflocal.ui;

final class ZoomModel {

    static final double[] STEPS = {0.25, 0.33, 0.5, 0.67, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};

    private static final double EPSILON = 0.005;

    private boolean fit = true;
    private double factor = 1.0;

    boolean isFit() {
        return fit;
    }

    void fit() {
        fit = true;
    }

    void actualSize() {
        fit = false;
        factor = 1.0;
    }

    double scale(double viewportWidth, double viewportHeight, double imageWidth, double imageHeight) {
        return fit ? fitScale(viewportWidth, viewportHeight, imageWidth, imageHeight) : factor;
    }

    void zoomIn(double currentScale) {
        fit = false;
        factor = STEPS[STEPS.length - 1];
        for (double step : STEPS) {
            if (step > currentScale + EPSILON) {
                factor = step;
                return;
            }
        }
    }

    void zoomOut(double currentScale) {
        fit = false;
        factor = STEPS[0];
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (STEPS[i] < currentScale - EPSILON) {
                factor = STEPS[i];
                return;
            }
        }
    }

    static double fitScale(double viewportWidth, double viewportHeight, double imageWidth, double imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return 1.0;
        }
        return Math.min(viewportWidth / imageWidth, viewportHeight / imageHeight);
    }

    static int percent(double scale) {
        return (int) Math.round(scale * 100);
    }
}
