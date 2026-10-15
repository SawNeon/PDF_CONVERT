package br.com.pdflocal.model;

import java.util.Objects;

public record ImageExportOptions(Format format, int dpi) {

    public enum Format {
        JPEG("jpg"),
        PNG("png");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    public static final int MIN_DPI = 72;
    public static final int MAX_DPI = 400;

    public ImageExportOptions {
        Objects.requireNonNull(format, "format");
        if (dpi < MIN_DPI || dpi > MAX_DPI) {
            throw new IllegalArgumentException("Invalid dpi: " + dpi);
        }
    }
}
