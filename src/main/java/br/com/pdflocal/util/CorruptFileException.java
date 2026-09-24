package br.com.pdflocal.util;

public class CorruptFileException extends PdfLocalException {

    public CorruptFileException(Throwable cause) {
        super(Messages.FILE_UNREADABLE, cause);
    }
}
