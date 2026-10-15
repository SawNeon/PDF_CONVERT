package br.com.pdflocal.service;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class StandardText {

    private static final double GLYPH_UNITS = 1000.0;

    private StandardText() {
    }

    static PDType1Font regular() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    static PDType1Font bold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    public static boolean canEncode(String text) {
        try {
            regular().encode(text);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    static double width(PDFont font, String text, double size) throws IOException {
        return font.getStringWidth(text) / GLYPH_UNITS * size;
    }
}
