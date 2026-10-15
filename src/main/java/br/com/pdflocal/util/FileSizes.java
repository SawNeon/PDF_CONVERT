package br.com.pdflocal.util;

import java.util.Locale;

public final class FileSizes {

    private static final double KILOBYTE = 1024.0;
    private static final double MEGABYTE = KILOBYTE * 1024;
    private static final double GIGABYTE = MEGABYTE * 1024;

    private FileSizes() {
    }

    public static String format(long bytes) {
        long safe = Math.max(0, bytes);
        if (safe < KILOBYTE) {
            return safe + " B";
        }
        if (safe < MEGABYTE) {
            return Math.round(safe / KILOBYTE) + " KB";
        }
        if (safe < GIGABYTE) {
            return decimal(safe / MEGABYTE) + " MB";
        }
        return decimal(safe / GIGABYTE) + " GB";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }
}
