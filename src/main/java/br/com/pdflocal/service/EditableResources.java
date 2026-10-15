package br.com.pdflocal.service;

import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

final class EditableResources {

    private static final List<COSName> CHILD_DICTIONARIES = List.of(
            COSName.FONT, COSName.EXT_G_STATE, COSName.XOBJECT, COSName.COLORSPACE,
            COSName.PATTERN, COSName.SHADING, COSName.PROPERTIES);

    private EditableResources() {
    }

    static PDResources privateCopy(PDPage page) {
        PDResources current = page.getResources();
        COSDictionary source = current == null ? new COSDictionary() : current.getCOSObject();
        COSDictionary copy = new COSDictionary(source);
        for (COSName key : CHILD_DICTIONARIES) {
            COSDictionary child = source.getCOSDictionary(key);
            if (child != null) {
                copy.setItem(key, new COSDictionary(child));
            }
        }
        PDResources resources = new PDResources(copy);
        page.setResources(resources);
        return resources;
    }
}
