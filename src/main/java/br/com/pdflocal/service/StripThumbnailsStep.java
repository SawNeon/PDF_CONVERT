package br.com.pdflocal.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

public final class StripThumbnailsStep implements OutputStep {

    @Override
    public void apply(PDDocument document, SaveListener listener) {
        for (PDPage page : document.getPages()) {
            page.getCOSObject().removeItem(COSName.THUMB);
        }
    }
}
