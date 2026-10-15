package br.com.pdflocal.util;

public class WrongPasswordException extends PasswordProtectedException {

    public WrongPasswordException(Throwable cause) {
        super(Messages.PDF_PASSWORD_WRONG, cause);
    }
}
