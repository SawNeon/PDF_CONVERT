package br.com.pdflocal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class LogSetup {

    private static final String FOLDER_NAME = "PdfLocal";
    private static final String LOGS_FOLDER = "logs";
    private static final String FILE_PATTERN = "pdflocal-%g.log";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int FILE_COUNT = 3;

    private LogSetup() {
    }

    public static Path defaultFolder() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".pdflocal")
                : Path.of(localAppData, FOLDER_NAME);
        return base.resolve(LOGS_FOLDER);
    }

    public static Handler install(Path folder) {
        Logger root = LogManager.getLogManager().getLogger("");
        try {
            Files.createDirectories(folder);
            FileHandler handler = new FileHandler(
                    folder.resolve(FILE_PATTERN).toString(), MAX_BYTES, FILE_COUNT, true);
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.INFO);
            root.addHandler(handler);
            return handler;
        } catch (IOException | RuntimeException e) {
            root.log(Level.WARNING, "Log file could not be created: {0}", e.getClass().getSimpleName());
            return null;
        }
    }

    public static void uninstall(Handler handler) {
        if (handler != null) {
            LogManager.getLogManager().getLogger("").removeHandler(handler);
            handler.close();
        }
    }
}
