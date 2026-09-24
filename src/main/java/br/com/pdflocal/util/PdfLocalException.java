package br.com.pdflocal.util;

public class PdfLocalException extends Exception {

    public PdfLocalException(String messageKey) {
        super(Messages.get(messageKey));
    }

    public PdfLocalException(String messageKey, Throwable cause) {
        super(Messages.get(messageKey), cause);
    }
}
