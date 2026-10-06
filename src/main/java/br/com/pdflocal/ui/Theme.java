package br.com.pdflocal.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.util.Locale;

public enum Theme {
    LIGHT,
    DARK;

    public String userAgentStylesheet() {
        return this == DARK ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet();
    }

    public Theme toggled() {
        return this == DARK ? LIGHT : DARK;
    }

    public static Theme parse(String value) {
        if (value != null && value.trim().toUpperCase(Locale.ROOT).equals(DARK.name())) {
            return DARK;
        }
        return LIGHT;
    }
}
