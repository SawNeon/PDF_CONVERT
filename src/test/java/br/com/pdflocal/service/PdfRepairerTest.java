package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import br.com.pdflocal.util.WrongPasswordException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfRepairerTest {

    private final PdfRepairer repairer = new PdfRepairer();

    @TempDir
    Path directory;

    @Test
    void aHealthyPdfIsCopiedWithAllPages() throws Exception {
        Path source = TestFiles.pdf(directory, "ok.pdf", "A1", "A2", "A3");
        Path output = directory.resolve("fixed.pdf");

        PdfRepairer.Report report = repairer.repair(source, output, null);

        assertEquals(3, report.pagesFound());
        assertEquals(3, report.pagesRecovered());
        assertEquals(0, report.pagesLost());
        assertEquals(0, report.pagesWithProblems());
        assertFalse(report.hadSignatures());
        assertEquals(List.of("A1", "A2", "A3"), TestFiles.pageTexts(output));
    }

    @Test
    void aBrokenCrossReferenceTableIsRebuilt() throws Exception {
        Path source = damaged("xref.pdf", bytes -> replace(bytes, "startxref", "startxref\n999999\n%%junk "));
        assertFalse(startxrefIsValid(source));

        Path output = directory.resolve("fixed.pdf");
        PdfRepairer.Report report = repairer.repair(source, output, null);

        assertEquals(3, report.pagesRecovered());
        assertEquals(List.of("A1", "A2", "A3"), TestFiles.pageTexts(output));
        assertTrue(startxrefIsValid(output));
        assertReadable(output, 3);
    }

    @Test
    void aMissingTrailerIsRecoveredFromTheObjects() throws Exception {
        Path source = damaged("cut.pdf", bytes -> {
            int cut = lastIndexOf(bytes, "xref");
            return Arrays.copyOf(bytes, Math.max(1, cut));
        });

        Path output = directory.resolve("fixed.pdf");
        PdfRepairer.Report report = repairer.repair(source, output, null);

        assertEquals(3, report.pagesRecovered());
        assertEquals(List.of("A1", "A2", "A3"), TestFiles.pageTexts(output));
        assertTrue(startxrefIsValid(output));
    }

    @Test
    void junkBeforeTheHeaderIsTolerated() throws Exception {
        Path source = damaged("junk.pdf", bytes -> {
            byte[] junk = "LIXO ANTES DO CABECALHO\r\n".repeat(4).getBytes(StandardCharsets.US_ASCII);
            byte[] result = Arrays.copyOf(junk, junk.length + bytes.length);
            System.arraycopy(bytes, 0, result, junk.length, bytes.length);
            return result;
        });

        Path output = directory.resolve("fixed.pdf");
        PdfRepairer.Report report = repairer.repair(source, output, null);

        assertEquals(3, report.pagesRecovered());
        assertEquals(List.of("A1", "A2", "A3"), TestFiles.pageTexts(output));
    }

    @Test
    void theOriginalFileIsNeverModified() throws Exception {
        Path source = damaged("xref.pdf", bytes -> replace(bytes, "startxref", "startxref\n999999\n%%junk "));
        String before = TestFiles.sha256(source);

        repairer.repair(source, directory.resolve("fixed.pdf"), null);

        assertEquals(before, TestFiles.sha256(source));
    }

    @Test
    void garbageThatIsNotRecoverableIsReportedAsCorrupt() throws Exception {
        Path source = TestFiles.corruptPdf(directory, "garbage.pdf");
        Path output = directory.resolve("fixed.pdf");

        assertThrows(CorruptFileException.class, () -> repairer.repair(source, output, null));
        assertTrue(Files.notExists(output));
    }

    @Test
    void filesThatAreNotPdfsAreRejected() throws Exception {
        Path text = TestFiles.fakeFile(directory, "note.pdf", "isto nao e um pdf");

        assertThrows(UnsupportedFileException.class, () -> repairer.repair(text, directory.resolve("out.pdf"), null));
    }

    @Test
    void theOutputCannotBeTheSourceFile() throws Exception {
        Path source = TestFiles.pdf(directory, "ok.pdf", "A1");
        String before = TestFiles.sha256(source);

        assertThrows(PdfLocalException.class, () -> repairer.repair(source, source, null));
        assertEquals(before, TestFiles.sha256(source));
    }

    @Test
    void aProtectedPdfAsksForThePasswordAndAWrongOneIsDistinguished() throws Exception {
        Path source = TestFiles.encryptedPdf(directory, "locked.pdf", "abre-sesamo-2026");

        assertThrows(PasswordProtectedException.class,
                () -> repairer.repair(source, directory.resolve("a.pdf"), null));
        assertThrows(WrongPasswordException.class,
                () -> repairer.repair(source, directory.resolve("b.pdf"), "senha-errada-1".toCharArray()));
    }

    @Test
    void withThePasswordTheRepairedCopyHasNoPassword() throws Exception {
        Path source = TestFiles.encryptedPdf(directory, "locked.pdf", "abre-sesamo-2026");
        Path output = directory.resolve("fixed.pdf");
        char[] password = "abre-sesamo-2026".toCharArray();

        repairer.repair(source, output, password);

        assertReadable(output, 1);
        char[] wiped = new char[password.length];
        assertArrayEquals(wiped, password);
    }

    @Test
    void signaturesAreReportedBecauseTheyAreNotKept() throws Exception {
        Path source = TestFiles.signedPdf(directory, "signed.pdf", "A1");

        PdfRepairer.Report report = repairer.repair(source, directory.resolve("fixed.pdf"), null);

        assertTrue(report.hadSignatures());
    }

    @Test
    void noTemporaryFilesAreLeftBehind() throws Exception {
        Path outputs = Files.createDirectory(directory.resolve("outputs"));
        Path source = TestFiles.corruptPdf(directory, "garbage.pdf");

        assertThrows(CorruptFileException.class, () -> repairer.repair(source, outputs.resolve("x.pdf"), null));

        try (var files = Files.list(outputs)) {
            assertEquals(0, files.count());
        }
    }

    private Path damaged(String name, java.util.function.UnaryOperator<byte[]> damage) throws IOException {
        Path healthy = TestFiles.pdf(directory, "healthy-" + name, "A1", "A2", "A3");
        Path file = directory.resolve(name);
        Files.write(file, damage.apply(Files.readAllBytes(healthy)));
        return file;
    }

    private static byte[] replace(byte[] bytes, String target, String replacement) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        int index = text.lastIndexOf(target);
        String result = text.substring(0, index) + replacement + text.substring(index + target.length());
        return result.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static int lastIndexOf(byte[] bytes, String target) {
        return new String(bytes, StandardCharsets.ISO_8859_1).lastIndexOf(target);
    }

    private static void assertReadable(Path file, int pages) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            assertEquals(pages, document.getNumberOfPages());
        }
    }

    private static boolean startxrefIsValid(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        int marker = text.lastIndexOf("startxref");
        if (marker < 0) {
            return false;
        }
        String[] tokens = text.substring(marker + "startxref".length()).strip().split("\\s+");
        long offset;
        try {
            offset = Long.parseLong(tokens[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (offset < 0 || offset >= bytes.length) {
            return false;
        }
        String at = text.substring((int) offset, (int) Math.min(text.length(), offset + 24));
        return at.startsWith("xref") || at.matches("(?s)\\d+ \\d+ obj.*");
    }
}
