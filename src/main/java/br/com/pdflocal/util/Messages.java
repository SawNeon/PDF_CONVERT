package br.com.pdflocal.util;

import java.text.MessageFormat;
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
    public static final String UNEXPECTED = "error.unexpected";
    public static final String SAVE_CANCELLED = "error.save.cancelled";
    public static final String SAVE_DENIED = "error.save.denied";
    public static final String SAVE_NO_SPACE = "error.save.no-space";
    public static final String SAVE_FOLDER_MISSING = "error.save.folder-missing";
    public static final String PDF_PASSWORD_WRONG = "error.pdf.password.wrong";
    public static final String FORM_MISSING = "error.form.missing";
    public static final String FORM_XFA = "error.form.xfa";
    public static final String FORM_VALUE_INVALID = "error.form.value";

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages", Locale.ROOT);

    private Messages() {
    }

    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    public static String format(String key, Object... arguments) {
        return new MessageFormat(get(key), Locale.ROOT).format(arguments);
    }
}
