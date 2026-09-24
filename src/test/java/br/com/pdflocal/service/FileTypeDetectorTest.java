package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    @TempDir
    Path directory;

    @Test
    void detectsPdf() throws Exception {
        assertEquals(FileType.PDF, detector.detect(TestFiles.pdf(directory, "a.pdf", "one")));
    }

    @ParameterizedTest
    @CsvSource({"jpg,JPEG", "png,PNG", "gif,GIF", "bmp,BMP", "tiff,TIFF"})
    void detectsImageFormats(String format, FileType expected) throws Exception {
        Path file = TestFiles.image(directory, "image.bin", format, 20, 20);

        assertEquals(expected, detector.detect(file));
    }

    @Test
    void ignoresFileExtension() throws Exception {
        Path pngNamedAsPdf = TestFiles.image(directory, "document.pdf", "png", 20, 20);

        assertEquals(FileType.PNG, detector.detect(pngNamedAsPdf));
    }

    @ParameterizedTest
    @CsvSource({"fake.pdf", "fake.jpg", "fake.png", "fake.tif", "fake.bmp"})
    void rejectsTextFileWithImageOrPdfExtension(String name) throws Exception {
        Path file = TestFiles.fakeFile(directory, name, "this is only plain text");

        assertThrows(UnsupportedFileException.class, () -> detector.detect(file));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        Path file = Files.createFile(directory.resolve("empty.pdf"));

        assertThrows(UnsupportedFileException.class, () -> detector.detect(file));
    }

    @Test
    void reportsMissingFileAsUnreadable() {
        Path missing = directory.resolve("missing.pdf");

        assertThrows(CorruptFileException.class, () -> detector.detect(missing));
    }

    @Test
    void unsupportedMessageIsUserFacing() throws IOException {
        Path file = TestFiles.fakeFile(directory, "fake.pdf", "text");

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> detector.detect(file));

        assertEquals("Tipo de arquivo não suportado. Use PDF ou imagens (JPEG, PNG, GIF, BMP, TIFF).",
                error.getMessage());
    }
}
