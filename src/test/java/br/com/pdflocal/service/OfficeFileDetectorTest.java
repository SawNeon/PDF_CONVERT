package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class OfficeFileDetectorTest {

    private final OfficeFileDetector detector = new OfficeFileDetector();

    @TempDir
    Path directory;

    @Test
    void openXmlFilesAreRecognizedByTheirParts() throws Exception {
        assertEquals(OfficeKind.WORD, detector.detect(zip("a.docx", "word/document.xml")));
        assertEquals(OfficeKind.EXCEL, detector.detect(zip("a.xlsx", "xl/workbook.xml")));
        assertEquals(OfficeKind.POWERPOINT, detector.detect(zip("a.pptx", "ppt/presentation.xml")));
    }

    @Test
    void theExtensionIsCaseInsensitive() throws Exception {
        assertEquals(OfficeKind.WORD, detector.detect(zip("A.DOCX", "word/document.xml")));
    }

    @Test
    void legacyFilesNeedTheOleSignature() throws Exception {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};

        assertEquals(OfficeKind.WORD, detector.detect(bytes("a.doc", ole)));
        assertEquals(OfficeKind.EXCEL, detector.detect(bytes("a.xls", ole)));
        assertEquals(OfficeKind.POWERPOINT, detector.detect(bytes("a.ppt", ole)));
        assertUnsupported(bytes("fake.doc", "isto nao e ole".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void rtfNeedsItsSignature() throws Exception {
        assertEquals(OfficeKind.WORD, detector.detect(bytes("a.rtf", "{\\rtf1\\ansi ola}".getBytes(StandardCharsets.US_ASCII))));
        assertUnsupported(bytes("b.rtf", "texto simples".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void theContentMustMatchTheExtension() throws Exception {
        assertUnsupported(zip("word-as-excel.xlsx", "word/document.xml"));
        assertUnsupported(zip("excel-as-word.docx", "xl/workbook.xml"));
        assertUnsupported(zip("empty.pptx", "docProps/core.xml"));
    }

    @Test
    void filesThatAreNotZipsAreRejectedEvenWithAnOfficeExtension() throws Exception {
        assertUnsupported(bytes("fake.docx", "MZ executavel disfarcado".getBytes(StandardCharsets.US_ASCII)));
        assertUnsupported(bytes("broken.xlsx", new byte[] {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5}));
    }

    @Test
    void macroEnabledContentIsRefused() throws Exception {
        Path file = zip("macro.docx", "word/document.xml", "word/vbaProject.bin");

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> detector.detect(file));

        assertEquals(Messages.get(Messages.CONVERT_MACROS), error.getMessage());
    }

    @Test
    void unknownExtensionsAreRejected() throws Exception {
        assertUnsupported(zip("a.docm", "word/document.xml"));
        assertUnsupported(zip("a.exe", "word/document.xml"));
        assertUnsupported(zip("noextension", "word/document.xml"));
    }

    private void assertUnsupported(Path file) {
        PdfLocalException error = assertThrows(PdfLocalException.class, () -> detector.detect(file));
        assertEquals(Messages.get(Messages.CONVERT_UNSUPPORTED), error.getMessage());
    }

    private Path bytes(String name, byte[] content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    private Path zip(String name, String... parts) throws IOException {
        Path file = directory.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String part : parts) {
                zip.putNextEntry(new ZipEntry(part));
                zip.write("<x/>".getBytes(StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }
        return file;
    }
}
