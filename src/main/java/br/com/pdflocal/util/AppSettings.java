package br.com.pdflocal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AppSettings {

    private static final Logger LOG = Logger.getLogger(AppSettings.class.getName());
    private static final String THEME_KEY = "theme";
    private static final String FOLDER_NAME = "PdfLocal";
    private static final String FILE_NAME = "settings.properties";

    private final Path file;
    private final Properties properties = new Properties();

    public AppSettings(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    public static AppSettings defaults() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".pdflocal")
                : Path.of(localAppData, FOLDER_NAME);
        return new AppSettings(base.resolve(FILE_NAME));
    }

    public String theme() {
        return properties.getProperty(THEME_KEY, "");
    }

    public void setTheme(String theme) {
        properties.setProperty(THEME_KEY, theme);
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Settings could not be read: {0}", e.getClass().getSimpleName());
            properties.clear();
        }
    }

    private void save() {
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, null);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Settings could not be saved: {0}", e.getClass().getSimpleName());
        }
    }
}
