package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRegistryTest {

    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void opensPdfAndCountsPages() throws Exception {
        Path file = TestFiles.pdf(directory, "a.pdf", "one", "two", "three");

        SourceDocument source = registry.open(file);

        assertEquals(3, source.pageCount());
        assertEquals(file, source.path());
        assertTrue(registry.contains(source.id()));
        assertEquals(3, registry.document(source.id()).getNumberOfPages());
    }

    @Test
    void eachOpenedFileGetsItsOwnId() throws Exception {
        Path file = TestFiles.pdf(directory, "a.pdf", "one");

        SourceDocument first = registry.open(file);
        SourceDocument second = registry.open(file);

        assertFalse(first.id().equals(second.id()));
        assertEquals(2, registry.sources().size());
    }

    @Test
    void passwordProtectedPdfIsReportedWithoutCrashing() throws Exception {
        Path file = TestFiles.encryptedPdf(directory, "locked.pdf", "s3cret");

        PasswordProtectedException error = assertThrows(PasswordProtectedException.class, () -> registry.open(file));

        assertEquals("PDF protegido por senha", error.getMessage());
        assertTrue(registry.sources().isEmpty());
    }

    @Test
    void corruptedPdfIsReportedWithoutCrashing() throws Exception {
        Path file = TestFiles.corruptPdf(directory, "broken.pdf");

        CorruptFileException error = assertThrows(CorruptFileException.class, () -> registry.open(file));

        assertEquals("Não foi possível ler o arquivo", error.getMessage());
        assertTrue(registry.sources().isEmpty());
    }

    @Test
    void textFileWithPdfExtensionIsRejected() throws Exception {
        Path file = TestFiles.fakeFile(directory, "fake.pdf", "not a pdf");

        assertThrows(UnsupportedFileException.class, () -> registry.open(file));
    }

    @Test
    void imageIsNotAcceptedAsSourcePdf() throws Exception {
        Path file = TestFiles.image(directory, "photo.pdf", "png", 10, 10);

        assertThrows(UnsupportedFileException.class, () -> registry.open(file));
    }

    @Test
    void closingOneSourceClosesOnlyThatDocument() throws Exception {
        SourceDocument first = registry.open(TestFiles.pdf(directory, "a.pdf", "one"));
        SourceDocument second = registry.open(TestFiles.pdf(directory, "b.pdf", "two"));
        PDDocument firstDocument = registry.document(first.id());
        PDDocument secondDocument = registry.document(second.id());

        registry.close(first.id());

        assertFalse(registry.contains(first.id()));
        assertTrue(firstDocument.getDocument().isClosed());
        assertTrue(registry.contains(second.id()));
        assertFalse(secondDocument.getDocument().isClosed());
    }

    @Test
    void closeReleasesEveryDocument() throws Exception {
        PDDocument first = registry.document(registry.open(TestFiles.pdf(directory, "a.pdf", "one")).id());
        PDDocument second = registry.document(registry.open(TestFiles.pdf(directory, "b.pdf", "two")).id());

        registry.close();

        assertTrue(first.getDocument().isClosed());
        assertTrue(second.getDocument().isClosed());
        assertTrue(registry.sources().isEmpty());
    }

    @Test
    void unknownIdIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> registry.document(UUID.randomUUID()));
    }
}
