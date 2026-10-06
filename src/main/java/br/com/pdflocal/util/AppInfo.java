package br.com.pdflocal.util;

public final class AppInfo {

    static final String VERSION_PROPERTY = "jpackage.app-version";
    static final String DEVELOPMENT_VERSION = "desenvolvimento";

    private AppInfo() {
    }

    public static String version() {
        String value = System.getProperty(VERSION_PROPERTY);
        return value == null || value.isBlank() ? DEVELOPMENT_VERSION : value.trim();
    }
}
