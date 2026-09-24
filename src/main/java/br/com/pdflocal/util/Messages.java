package br.com.pdflocal.util;

import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {

    public static final String PDF_PASSWORD_PROTECTED = "error.pdf.password";
    public static final String FILE_UNREADABLE = "error.file.unreadable";
    public static final String FILE_UNSUPPORTED = "error.file.unsupported";
    public static final String SAVE_EMPTY = "error.save.empty";
    public static final String SAVE_OVERWRITES_SOURCE = "error.save.overwrites-source";
    public static final String SAVE_FAILED = "error.save.failed";
    public static final String PAGE_MISSING = "error.page.missing";

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages", Locale.ROOT);

    private Messages() {
    }

    public static String get(String key) {
        return BUNDLE.getString(key);
    }
}
