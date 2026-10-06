package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.testsupport.TempDirCleanup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class LogSetupTest {

    @TempDir
    Path directory;

    @Test
    void writesLogRecordsToAFileInTheGivenFolder() throws Exception {
        Path folder = directory.resolve("logs");
        Handler handler = LogSetup.install(folder);
        assertNotNull(handler);

        Logger.getLogger("test.logsetup").info("operation finished with 3 pages");
        handler.flush();
        LogSetup.uninstall(handler);

        try (Stream<Path> files = Files.list(folder)) {
            String content = files.filter(path -> path.getFileName().toString().endsWith(".log"))
                    .map(LogSetupTest::read)
                    .reduce("", String::concat);
            assertTrue(content.contains("operation finished with 3 pages"));
        }
    }

    @Test
    void createsTheMissingFolderTree() {
        Path folder = directory.resolve("a").resolve("b").resolve("logs");

        Handler handler = LogSetup.install(folder);
        LogSetup.uninstall(handler);

        assertTrue(Files.isDirectory(folder));
    }

    @Test
    void anUnusableFolderDoesNotThrow() throws Exception {
        Path blocker = Files.writeString(directory.resolve("blocker"), "file");

        Handler handler = LogSetup.install(blocker.resolve("logs"));

        assertNull(handler);
    }

    @Test
    void uninstallStopsWritingAndToleratesNull() throws Exception {
        Path folder = directory.resolve("logs");
        Handler handler = LogSetup.install(folder);
        LogSetup.uninstall(handler);

        Logger.getLogger("test.logsetup").info("after uninstall");

        try (Stream<Path> files = Files.list(folder)) {
            assertFalse(files.map(LogSetupTest::read).anyMatch(text -> text.contains("after uninstall")));
        }
        LogSetup.uninstall(null);
    }

    @Test
    void defaultFolderEndsWithLogs() {
        assertTrue(LogSetup.defaultFolder().endsWith("logs"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
