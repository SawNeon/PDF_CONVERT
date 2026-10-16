package br.com.pdflocal.service;

import java.util.Optional;
import java.util.regex.Pattern;

final class CellValues {

    private static final Pattern SMALL_INTEGER = Pattern.compile("^-?(0|[1-9]\\d{0,5})$");
    private static final Pattern GROUPED_DECIMAL = Pattern.compile("^-?(0|[1-9]\\d{0,2})(\\.\\d{3})*,\\d{1,6}$");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^-?(0|[1-9]\\d{0,14}),\\d{1,6}$");

    private CellValues() {
    }

    static Optional<String> asNumber(String text) {
        String value = text.strip();
        if (SMALL_INTEGER.matcher(value).matches()) {
            return Optional.of(value);
        }
        if (GROUPED_DECIMAL.matcher(value).matches() || PLAIN_DECIMAL.matcher(value).matches()) {
            return Optional.of(value.replace(".", "").replace(',', '.'));
        }
        return Optional.empty();
    }
}
