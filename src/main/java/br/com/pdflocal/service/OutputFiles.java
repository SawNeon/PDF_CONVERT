package br.com.pdflocal.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

final class OutputFiles {

    private static final long LOW_SPACE_BYTES = 1024 * 1024;
    private static final Logger LOG = Logger.getLogger(OutputFiles.class.getName());

    private OutputFiles() {
    }

    static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete temporary file: {0}", e.getClass().getSimpleName());
        }
    }

    static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to delete temporary item: {0}", e.getClass().getSimpleName());
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to clean temporary folder: {0}", e.getClass().getSimpleName());
        }
    }

    static long usableSpace(Path directory) {
        try {
            return Files.getFileStore(directory).getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    static boolean isSameFile(Path a, Path b) {
        try {
            return Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    static boolean isLowOnSpace(Exception error, long usableSpace) {
        return error instanceof IOException && usableSpace >= 0 && usableSpace < LOW_SPACE_BYTES;
    }

    static boolean isAccessDenied(Exception error) {
        return error instanceof AccessDeniedException;
    }
}
