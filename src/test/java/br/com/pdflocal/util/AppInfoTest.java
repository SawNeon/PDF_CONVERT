package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppInfoTest {

    private String original;

    @BeforeEach
    void remember() {
        original = System.getProperty(AppInfo.VERSION_PROPERTY);
    }

    @AfterEach
    void restore() {
        if (original == null) {
            System.clearProperty(AppInfo.VERSION_PROPERTY);
        } else {
            System.setProperty(AppInfo.VERSION_PROPERTY, original);
        }
    }

    @Test
    void usesThePackagedVersionWhenPresent() {
        System.setProperty(AppInfo.VERSION_PROPERTY, " 1.2.3 ");

        assertEquals("1.2.3", AppInfo.version());
    }

    @Test
    void fallsBackToDevelopmentWhenMissingOrBlank() {
        System.clearProperty(AppInfo.VERSION_PROPERTY);
        assertEquals("desenvolvimento", AppInfo.version());

        System.setProperty(AppInfo.VERSION_PROPERTY, "  ");
        assertEquals("desenvolvimento", AppInfo.version());
    }
}
