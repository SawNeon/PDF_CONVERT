package br.com.pdflocal.util;

import java.util.Objects;

public final class OutputNames {

    private static final String FALLBACK_BASE = "documento";
    private static final String INVALID_CHARACTERS = "[\\\\/:*?\"<>|]";

    private OutputNames() {
    }

    public static String suggest(String sourceFileName, String suffix) {
        return suggest(sourceFileName, suffix, "pdf");
    }

    public static String suggest(String sourceFileName, String suffix, String extension) {
        Objects.requireNonNull(suffix, "suffix");
        Objects.requireNonNull(extension, "extension");
        String base = sourceFileName == null ? "" : sourceFileName.strip();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll(INVALID_CHARACTERS, "").strip();
        if (base.isEmpty() || base.startsWith(".")) {
            base = FALLBACK_BASE;
        }
        return base + suffix + "." + extension;
    }
}
