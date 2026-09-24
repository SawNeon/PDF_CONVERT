package br.com.pdflocal.util;

public class UnsupportedFileException extends PdfLocalException {

    public UnsupportedFileException() {
        super(Messages.FILE_UNSUPPORTED);
    }
}
