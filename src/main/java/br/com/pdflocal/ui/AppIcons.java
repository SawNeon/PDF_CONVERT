package br.com.pdflocal.ui;

import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class AppIcons {

    static final List<String> PATHS = List.of(
            "/images/app-icon-32.png", "/images/app-icon-64.png", "/images/app-icon-256.png");

    private static List<Image> icons;

    private AppIcons() {
    }

    public static synchronized List<Image> all() {
        if (icons == null) {
            icons = PATHS.stream()
                    .map(path -> new Image(Objects.requireNonNull(AppIcons.class.getResource(path), path).toExternalForm()))
                    .toList();
        }
        return icons;
    }

    public static void apply(Stage stage) {
        stage.getIcons().setAll(all());
    }
}
