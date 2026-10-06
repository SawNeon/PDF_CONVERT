package br.com.pdflocal.testsupport;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;

public final class TempDirCleanup implements AfterEachCallback {

    private static final long TIMEOUT_MILLIS = 5_000;
    private static final long STEP_MILLIS = 50;

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Object instance = context.getRequiredTestInstance();
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(TempDir.class) && Path.class.equals(field.getType())) {
                field.setAccessible(true);
                Path directory = (Path) field.get(instance);
                if (directory != null) {
                    emptyWithRetries(directory);
                }
            }
        }
    }

    private static void emptyWithRetries(Path directory) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        IOException last;
        do {
            try {
                deleteChildren(directory);
                Files.deleteIfExists(directory);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(STEP_MILLIS);
            }
        } while (System.currentTimeMillis() < deadline);
        throw last;
    }

    private static void deleteChildren(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    deleteChildren(child);
                }
                Files.deleteIfExists(child);
            }
        }
    }
}
