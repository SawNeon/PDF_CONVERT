package br.com.pdflocal.model;

public enum CompressionLevel {
    NONE(0, 0f),
    LIGHT(200, 0.80f),
    MEDIUM(150, 0.65f),
    STRONG(100, 0.50f);

    private final int targetDpi;
    private final float jpegQuality;

    CompressionLevel(int targetDpi, float jpegQuality) {
        this.targetDpi = targetDpi;
        this.jpegQuality = jpegQuality;
    }

    public int targetDpi() {
        return targetDpi;
    }

    public float jpegQuality() {
        return jpegQuality;
    }

    public boolean isEnabled() {
        return this != NONE;
    }
}
