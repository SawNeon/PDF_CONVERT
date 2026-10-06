package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AppearanceTest {

    @AfterEach
    void restoreDefault() {
        Appearance.apply(new Pane(), Theme.LIGHT);
    }

    @Test
    void darkThemeAddsTheDarkClass() {
        Pane root = new Pane();

        Appearance.apply(root, Theme.DARK);

        assertTrue(root.getStyleClass().contains(Appearance.DARK_CLASS));
        assertEquals(Theme.DARK, Appearance.current());
    }

    @Test
    void lightThemeRemovesTheDarkClass() {
        Pane root = new Pane();
        Appearance.apply(root, Theme.DARK);

        Appearance.apply(root, Theme.LIGHT);

        assertFalse(root.getStyleClass().contains(Appearance.DARK_CLASS));
        assertEquals(Theme.LIGHT, Appearance.current());
    }

    @Test
    void applyingTwiceDoesNotDuplicateClassOrStylesheet() {
        Pane root = new Pane();

        Appearance.apply(root, Theme.DARK);
        Appearance.apply(root, Theme.DARK);

        assertEquals(1, root.getStyleClass().stream().filter(Appearance.DARK_CLASS::equals).count());
        assertEquals(1, root.getStylesheets().size());
    }

    @Test
    void newViewsFollowTheCurrentTheme() {
        Appearance.apply(new Pane(), Theme.DARK);
        Pane later = new Pane();

        Appearance.style(later);

        assertTrue(later.getStyleClass().contains(Appearance.DARK_CLASS));
        assertTrue(later.getStylesheets().get(0).endsWith("pdflocal.css"));
    }
}
