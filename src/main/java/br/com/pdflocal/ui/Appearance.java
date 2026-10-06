package br.com.pdflocal.ui;

import java.util.Objects;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;

final class Appearance {

    static final String DARK_CLASS = "dark";
    private static final String STYLESHEET = "/pdflocal.css";

    private static Theme current = Theme.LIGHT;

    private Appearance() {
    }

    static String stylesheet() {
        return Objects.requireNonNull(Appearance.class.getResource(STYLESHEET), STYLESHEET).toExternalForm();
    }

    static Theme current() {
        return current;
    }

    static void apply(Parent root, Theme theme) {
        current = theme;
        style(root);
    }

    static void style(Parent root) {
        String sheet = stylesheet();
        if (!root.getStylesheets().contains(sheet)) {
            root.getStylesheets().add(sheet);
        }
        root.getStyleClass().remove(DARK_CLASS);
        if (current == Theme.DARK) {
            root.getStyleClass().add(DARK_CLASS);
        }
    }

    static void style(Dialog<?> dialog) {
        style(dialog.getDialogPane());
        dialog.setOnShowing(event -> {
            if (dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                AppIcons.apply(stage);
            }
        });
    }
}
