package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.testsupport.TempDirCleanup;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class AppSettingsTest {

    @TempDir
    Path directory;

    @Test
    void missingFileGivesEmptyTheme() {
        assertEquals("", new AppSettings(directory.resolve("settings.properties")).theme());
    }

    @Test
    void savedThemeIsReadBackByANewInstance() {
        Path file = directory.resolve("settings.properties");

        new AppSettings(file).setTheme("DARK");

        assertEquals("DARK", new AppSettings(file).theme());
    }

    @Test
    void createsTheMissingFolder() {
        Path file = directory.resolve("nested").resolve("PdfLocal").resolve("settings.properties");

        new AppSettings(file).setTheme("LIGHT");

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void unreadableFileIsIgnored() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.write(file, new byte[] {'\\', 'u', 'x', 'x'});

        assertEquals("", new AppSettings(file).theme());
    }

    @Test
    void failureToSaveDoesNotThrow() throws Exception {
        Path blocker = Files.writeString(directory.resolve("blocker"), "file");
        AppSettings settings = new AppSettings(blocker.resolve("settings.properties"));

        settings.setTheme("DARK");

        assertEquals("DARK", settings.theme());
    }
}
