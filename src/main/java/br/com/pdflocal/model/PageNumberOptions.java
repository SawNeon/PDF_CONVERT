package br.com.pdflocal.model;

import java.util.Objects;

public record PageNumberOptions(Position position, String pattern, int startNumber, boolean skipFirstPage,
                                int fontSize) {

    public enum Position {
        BOTTOM_CENTER, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, TOP_LEFT, TOP_RIGHT;

        public boolean isTop() {
            return this == TOP_CENTER || this == TOP_LEFT || this == TOP_RIGHT;
        }

        public boolean isLeft() {
            return this == BOTTOM_LEFT || this == TOP_LEFT;
        }

        public boolean isRight() {
            return this == BOTTOM_RIGHT || this == TOP_RIGHT;
        }
    }

    public static final String NUMBER_TOKEN = "{n}";
    public static final String TOTAL_TOKEN = "{total}";
    public static final int MAX_PATTERN_LENGTH = 60;
    public static final int MIN_FONT_SIZE = 6;
    public static final int MAX_FONT_SIZE = 24;
    public static final int MAX_START_NUMBER = 99_999;

    public PageNumberOptions {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(pattern, "pattern");
        if (!pattern.contains(NUMBER_TOKEN) || pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Invalid pattern");
        }
        if (startNumber < 1 || startNumber > MAX_START_NUMBER) {
            throw new IllegalArgumentException("Invalid start number: " + startNumber);
        }
        if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
            throw new IllegalArgumentException("Invalid font size: " + fontSize);
        }
    }

    public String render(int number, int total) {
        return pattern.replace(NUMBER_TOKEN, String.valueOf(number)).replace(TOTAL_TOKEN, String.valueOf(total));
    }
}
