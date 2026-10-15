package br.com.pdflocal.model;

import java.nio.file.Path;
import java.util.Objects;

public record WatermarkOptions(String text, Path imagePath, double opacity, boolean diagonal) {

    public static final int MAX_TEXT_LENGTH = 40;
    public static final double MIN_OPACITY = 0.05;
    public static final double MAX_OPACITY = 1.0;

    public WatermarkOptions {
        boolean hasText = text != null && !text.isBlank();
        boolean hasImage = imagePath != null;
        if (hasText == hasImage) {
            throw new IllegalArgumentException("A watermark is either a text or an image");
        }
        if (hasText && (text.length() > MAX_TEXT_LENGTH || text.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Invalid watermark text");
        }
        if (!(opacity >= MIN_OPACITY && opacity <= MAX_OPACITY)) {
            throw new IllegalArgumentException("Invalid opacity: " + opacity);
        }
        text = hasText ? text.strip() : null;
    }

    public static WatermarkOptions ofText(String text, double opacity, boolean diagonal) {
        Objects.requireNonNull(text, "text");
        return new WatermarkOptions(text, null, opacity, diagonal);
    }

    public static WatermarkOptions ofImage(Path imagePath, double opacity, boolean diagonal) {
        Objects.requireNonNull(imagePath, "imagePath");
        return new WatermarkOptions(null, imagePath, opacity, diagonal);
    }

    public boolean isText() {
        return text != null;
    }
}
