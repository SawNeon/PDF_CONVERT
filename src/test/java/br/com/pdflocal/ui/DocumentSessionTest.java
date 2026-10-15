package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageNumberOptions;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.service.ImportResult;
import br.com.pdflocal.service.PageNumbersStep;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class DocumentSessionTest {

    private static final long TIMEOUT_SECONDS = 30;

    private final DocumentSession session = new DocumentSession();

    @TempDir
    Path directory;

    private Path pdfA;
    private Path pdfB;

    @BeforeEach
    void createSources() throws Exception {
        pdfA = TestFiles.pdf(directory, "a.pdf", "A1", "A2", "A3");
        pdfB = TestFiles.pdf(directory, "b.pdf", "B1", "B2");
    }

    @AfterEach
    void closeSession() {
        session.close();
    }

    @Test
    void rotatedPagesAreRotatedInTheSavedPdf() throws Exception {
        load(pdfA);

        session.rotate(List.of(0), 90);
        session.rotate(List.of(1), -90);
        session.rotate(List.of(2), 90);
        session.rotate(List.of(2), 90);
        Path output = save();

        assertEquals(List.of(90, 270, 180), rotations(output));
    }

    @Test
    void imageShareReflectsTheListedPagesOnly() throws Exception {
        Path scan = TestFiles.scannedPdf(directory, "scan.pdf", 1, 1200, 1700, 0.9f, false);
        load(scan, pdfA);

        double mixed = session.imageShare().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).share();
        session.delete(List.of(1, 2, 3));
        double scanOnly = session.imageShare().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).share();

        assertTrue(scanOnly > mixed, "mixed " + mixed + " scanOnly " + scanOnly);
        assertTrue(scanOnly > 0.9);
    }

    @Test
    void savingWithStepsAppliesThemAndReportsStepProgress() throws Exception {
        load(pdfA);
        Path output = directory.resolve("steps.pdf");
        List<Integer> progress = new CopyOnWriteArrayList<>();
        PageNumberOptions options = new PageNumberOptions(PageNumberOptions.Position.BOTTOM_CENTER, "{n}", 1, false, 12);

        DocumentSession.SaveOperation operation = session.startSave(output, PageSize.A4,
                List.of(new PageNumbersStep(options)), (done, total) -> { }, (done, total) -> progress.add(done));
        operation.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(List.of("A11", "A22", "A33"),
                texts(output).stream().map(text -> text.replaceAll("\\s+", "")).toList());
        assertEquals(List.of(1, 2, 3), progress);
    }

    @Test
    void exportingProducesAZipWithOneImagePerListedPage() throws Exception {
        load(pdfA);
        session.delete(List.of(1));
        Path output = directory.resolve("images.zip");
        List<Integer> progress = new CopyOnWriteArrayList<>();

        DocumentSession.SaveOperation operation = session.startExport(output, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.JPEG, 72), (done, total) -> { },
                (done, total) -> progress.add(done));
        operation.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(output.toFile())) {
            assertEquals(List.of("pagina-001.jpg", "pagina-002.jpg"),
                    zip.stream().map(java.util.zip.ZipEntry::getName).toList());
        }
        assertTrue(progress.contains(2));
    }

    @Test
    void repairingWritesAHealthyCopyAndLeavesTheSourceAlone() throws Exception {
        Path output = directory.resolve("repaired.pdf");
        String before = TestFiles.sha256(pdfA);

        br.com.pdflocal.service.PdfRepairer.Report report =
                session.repair(pdfA, output, null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(3, report.pagesRecovered());
        assertEquals(List.of("A1", "A2", "A3"), texts(output));
        assertEquals(before, TestFiles.sha256(pdfA));
    }

    @Test
    void deletedPagesAreMissingFromTheSavedPdf() throws Exception {
        load(pdfA, pdfB);

        session.delete(List.of(1, 3));

        assertEquals(List.of("A1", "A3", "B2"), texts(save()));
    }

    @Test
    void undoRestoresADeletedPageAndItAppearsInTheSavedPdf() throws Exception {
        load(pdfA);
        session.delete(List.of(1));

        assertTrue(session.undo());

        assertEquals(List.of("A1", "A2", "A3"), texts(save()));
    }

    @Test
    void undoRevertsARotation() throws Exception {
        load(pdfA);
        session.rotate(List.of(0, 1), 90);

        session.undo();

        assertEquals(List.of(0, 0, 0), rotations(save()));
    }

    @Test
    void undoRevertsAMove() throws Exception {
        load(pdfA);
        session.move(List.of(0), 3);
        assertEquals(List.of("A2", "A3", "A1"), texts(save()));

        session.undo();

        assertEquals(List.of("A1", "A2", "A3"), texts(save()));
    }

    @Test
    void undoGoesBackStepByStep() throws Exception {
        load(pdfA);
        session.rotate(List.of(0), 90);
        session.delete(List.of(2));
        session.move(List.of(0), 2);

        session.undo();
        assertEquals(List.of("A1", "A2"), texts(save()));
        session.undo();
        assertEquals(List.of("A1", "A2", "A3"), texts(save()));
        assertEquals(List.of(90, 0, 0), rotations(save()));
        session.undo();
        assertEquals(List.of(0, 0, 0), rotations(save()));
        assertFalse(session.undo());
        assertFalse(session.canUndo());
    }

    @Test
    void undoWithNothingToUndoDoesNothing() {
        assertFalse(session.undo());
        assertFalse(session.canUndo());
    }

    @Test
    void movingSeveralPagesKeepsThemAsABlock() throws Exception {
        load(pdfA, pdfB);

        session.move(List.of(0, 1), 5);

        assertEquals(List.of("A3", "B1", "B2", "A1", "A2"), texts(save()));
    }

    @Test
    void anEditThatChangesNothingIsNotUndoable() throws Exception {
        load(pdfA);

        session.move(List.of(1), 1);
        session.move(List.of(1), 2);
        session.rotate(List.of(), 90);
        session.delete(List.of());

        assertFalse(session.canUndo());
    }

    @Test
    void imagePageSizeIsUsedWhenSaving() throws Exception {
        Path photo = TestFiles.image(directory, "photo.png", "png", 200, 100);
        load(photo);

        try (PDDocument original = Loader.loadPDF(save(PageSize.ORIGINAL).toFile());
                PDDocument a4 = Loader.loadPDF(save(PageSize.A4).toFile())) {
            assertEquals(200, original.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(841.89, a4.getPage(0).getMediaBox().getWidth(), 0.01);
        }
    }

    @Test
    void aFileWhosePagesWereAllDeletedStaysOpenWhileItCanStillBeRestored() throws Exception {
        load(pdfA, pdfB);

        session.delete(List.of(3, 4));
        session.whenIdle().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(2, session.openSourceCount());
        session.undo();
        assertEquals(List.of("A1", "A2", "A3", "B1", "B2"), texts(save()));
    }

    @Test
    void aFileWhosePagesWereAllDeletedIsClosedOnceUndoCannotReachIt() throws Exception {
        load(pdfA, pdfB);
        session.delete(List.of(3, 4));

        for (int i = 0; i < 30; i++) {
            session.rotate(List.of(0), 90);
        }
        session.whenIdle().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(1, session.openSourceCount());
        assertEquals(List.of("A1", "A2", "A3"), texts(save()));
    }

    @Test
    void aFileStillInTheListIsNeverClosed() throws Exception {
        load(pdfA, pdfB);

        for (int i = 0; i < 35; i++) {
            session.rotate(List.of(0), 90);
        }
        session.whenIdle().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(2, session.openSourceCount());
    }

    @Test
    void aFileOpenedButNotYetAddedToTheListIsNotClosedByAnEdit() throws Exception {
        load(pdfA);
        ImportResult pending = session.importFiles(List.of(pdfB)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        session.rotate(List.of(0), 90);
        session.whenIdle().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, session.openSourceCount());

        session.append(pending.items());
        assertEquals(List.of("A1", "A2", "A3", "B1", "B2"), texts(save()));
    }

    @Test
    void clearRemovesEverythingAndForgetsTheHistory() throws Exception {
        load(pdfA, pdfB);
        session.delete(List.of(0));

        session.clear().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(session.items().isEmpty());
        assertEquals(0, session.openSourceCount());
        assertFalse(session.canUndo());
    }

    @Test
    void savingNeverModifiesTheOriginals() throws Exception {
        String hashA = TestFiles.sha256(pdfA);
        String hashB = TestFiles.sha256(pdfB);
        load(pdfA, pdfB);
        session.rotate(List.of(0, 3), 90);
        session.delete(List.of(1));
        session.move(List.of(0), 4);

        save();
        session.close();

        assertEquals(hashA, TestFiles.sha256(pdfA));
        assertEquals(hashB, TestFiles.sha256(pdfB));
    }

    @Test
    void signedSourcesAreDetectedOnlyWhileTheirPagesAreInTheList() throws Exception {
        Path signed = TestFiles.signedPdf(directory, "signed.pdf", "S1");
        load(pdfA, signed);
        assertTrue(session.hasSignedSources());

        session.delete(List.of(3));

        assertFalse(session.hasSignedSources());
    }

    @Test
    void startSaveReportsProgressUpToTheTotal() throws Exception {
        load(pdfA, pdfB);
        List<String> progress = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        DocumentSession.SaveOperation operation = session.startSave(
                directory.resolve("progress.pdf"), PageSize.A4, (done, total) -> progress.add(done + "/" + total));

        assertEquals(5, operation.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).pages());
        assertEquals(List.of("1/5", "2/5", "3/5", "4/5", "5/5"), progress);
    }

    @Test
    void cancellingAnOperationStopsTheSaveAndLeavesNoFile() throws Exception {
        load(pdfA, pdfB);
        Path output = directory.resolve("cancelled.pdf");
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        DocumentSession.SaveOperation operation = session.startSave(output, PageSize.A4, (done, total) -> {
            started.countDown();
            try {
                release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        operation.cancel().run();
        release.countDown();

        java.util.concurrent.ExecutionException error = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.ExecutionException.class, () -> operation.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(error.getCause().getCause() instanceof br.com.pdflocal.util.SaveCancelledException
                || error.getCause() instanceof br.com.pdflocal.util.SaveCancelledException);
        assertFalse(java.nio.file.Files.exists(output));
    }

    @Test
    void sourceBytesSumsEachDistinctFileOnce() throws Exception {
        Path photo = TestFiles.image(directory, "photo.png", "png", 30, 30);
        load(pdfA, pdfB, photo);
        long expected = java.nio.file.Files.size(pdfA) + java.nio.file.Files.size(pdfB) + java.nio.file.Files.size(photo);

        assertEquals(expected, session.sourceBytes());

        session.delete(List.of(3, 4));
        assertEquals(java.nio.file.Files.size(pdfA) + java.nio.file.Files.size(photo), session.sourceBytes());
    }

    @Test
    void compressedSaveReportsTheCompressionAndIsSmaller() throws Exception {
        Path scan = TestFiles.scannedPdf(directory, "scan.pdf", 2, 2000, 2800, 0.9f, false);
        load(scan);
        List<String> progress = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        DocumentSession.SaveOperation compressed = session.startSave(
                directory.resolve("small.pdf"), PageSize.A4, br.com.pdflocal.model.CompressionLevel.MEDIUM,
                (done, total) -> { }, (done, total) -> progress.add(done + "/" + total));
        br.com.pdflocal.service.SaveResult result = compressed.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(2, result.pages());
        assertEquals(2, result.compression().replaced());
        assertEquals(List.of("1/2", "2/2"), progress);
        assertTrue(result.outputBytes() < java.nio.file.Files.size(scan) / 2);
        assertEquals(java.nio.file.Files.size(directory.resolve("small.pdf")), result.outputBytes());
    }

    @Test
    void fileCountCountsFilesNotPages() throws Exception {
        Path photo = TestFiles.image(directory, "photo.png", "png", 20, 20);

        load(pdfA, pdfB, photo);

        assertEquals(6, session.items().size());
        assertEquals(3, session.fileCount());
    }

    private void load(Path... files) throws Exception {
        ImportResult result = session.importFiles(List.of(files)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(result.failures().isEmpty());
        session.append(result.items());
    }

    private Path save() throws Exception {
        return save(PageSize.A4);
    }

    private Path save(PageSize size) throws Exception {
        Path output = directory.resolve("out-" + size + "-" + System.nanoTime() + ".pdf");
        session.save(output, size).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return output;
    }

    private static List<String> texts(Path pdf) throws Exception {
        return TestFiles.pageTexts(pdf).stream().map(text -> text.replaceAll("\\s+", "")).toList();
    }

    private static List<Integer> rotations(Path pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return IntStream.range(0, document.getNumberOfPages())
                    .mapToObj(i -> document.getPage(i).getRotation())
                    .toList();
        }
    }
}
