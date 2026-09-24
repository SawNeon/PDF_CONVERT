package br.com.pdflocal.util;

public class PasswordProtectedException extends PdfLocalException {

    public PasswordProtectedException(Throwable cause) {
        super(Messages.PDF_PASSWORD_PROTECTED, cause);
    }
}
