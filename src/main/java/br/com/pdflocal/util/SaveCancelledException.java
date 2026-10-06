package br.com.pdflocal.util;

public class SaveCancelledException extends PdfLocalException {

    public SaveCancelledException() {
        super(Messages.SAVE_CANCELLED);
    }
}
