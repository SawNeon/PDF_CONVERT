package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThemeTest {

    @Test
    void parsesKnownValuesIgnoringCaseAndSpaces() {
        assertEquals(Theme.DARK, Theme.parse("DARK"));
        assertEquals(Theme.DARK, Theme.parse(" dark "));
        assertEquals(Theme.LIGHT, Theme.parse("LIGHT"));
    }

    @Test
    void unknownOrMissingValuesFallBackToLight() {
        assertEquals(Theme.LIGHT, Theme.parse(null));
        assertEquals(Theme.LIGHT, Theme.parse(""));
        assertEquals(Theme.LIGHT, Theme.parse("purple"));
    }

    @Test
    void toggleSwitchesBetweenTheTwoThemes() {
        assertEquals(Theme.DARK, Theme.LIGHT.toggled());
        assertEquals(Theme.LIGHT, Theme.DARK.toggled());
    }

    @Test
    void eachThemeHasItsOwnStylesheet() {
        assertNotEquals(Theme.LIGHT.userAgentStylesheet(), Theme.DARK.userAgentStylesheet());
        assertTrue(Theme.DARK.userAgentStylesheet().contains("primer-dark"));
        assertTrue(Theme.LIGHT.userAgentStylesheet().contains("primer-light"));
    }
}
