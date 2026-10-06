package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
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
    void closingWaitsForTheDocumentToBeReleasedByWhoIsUsingIt() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "one"));
        PDDocument document = registry.document(source.id());
        ReentrantLock lock = registry.lock(source.id());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        lock.lock();
        Future<?> closing = executor.submit(() -> registry.close(source.id()));
        try {
            assertThrows(TimeoutException.class, () -> closing.get(300, TimeUnit.MILLISECONDS));
            assertFalse(document.getDocument().isClosed());
        } finally {
            lock.unlock();
        }

        closing.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(document.getDocument().isClosed());
        assertFalse(registry.contains(source.id()));
    }

    @Test
    void plainPdfIsNotMarkedAsSigned() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "one"));

        assertFalse(source.signed());
    }

    @Test
    void signedPdfIsMarkedAsSigned() throws Exception {
        SourceDocument source = registry.open(TestFiles.signedPdf(directory, "signed.pdf", "one", "two"));

        assertTrue(source.signed());
        assertEquals(2, source.pageCount());
    }

    @Test
    void anySignedLooksOnlyAtSourcesStillUsedByTheList() throws Exception {
        SourceDocument plain = registry.open(TestFiles.pdf(directory, "a.pdf", "one"));
        SourceDocument signed = registry.open(TestFiles.signedPdf(directory, "s.pdf", "one"));
        Path photo = TestFiles.image(directory, "photo.png", "png", 10, 10);

        assertFalse(registry.anySigned(List.of(PageItem.pdfPage(plain.id(), 0), PageItem.image(photo))));
        assertTrue(registry.anySigned(List.of(PageItem.pdfPage(plain.id(), 0), PageItem.pdfPage(signed.id(), 0))));
        assertFalse(registry.anySigned(List.of()));
    }

    @Test
    void unknownIdIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> registry.document(UUID.randomUUID()));
    }
}
