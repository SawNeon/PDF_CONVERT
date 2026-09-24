package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentComposerTest {

    private static final double DELTA = 0.01;

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    private Path inputs;
    private Path outputs;

    @BeforeEach
    void createDirectories() throws IOException {
        inputs = Files.createDirectory(directory.resolve("inputs"));
        outputs = Files.createDirectory(directory.resolve("outputs"));
    }

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void mergesTwoPdfsInOrder() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2"));
        SourceDocument b = registry.open(TestFiles.pdf(inputs, "b.pdf", "B1", "B2", "B3"));
        Path output = outputs.resolve("merged.pdf");

        composer.compose(
                List.of(page(a, 0), page(a, 1), page(b, 0), page(b, 1), page(b, 2)),
                registry, PageSize.ORIGINAL, output);

        assertEquals(List.of("A1", "A2", "B1", "B2", "B3"), TestFiles.pageTexts(output));
    }

    @Test
    void reordersPages() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2"));
        SourceDocument b = registry.open(TestFiles.pdf(inputs, "b.pdf", "B1", "B2"));
        Path output = outputs.resolve("reordered.pdf");

        composer.compose(List.of(page(b, 1), page(a, 0), page(b, 0), page(a, 1)),
                registry, PageSize.ORIGINAL, output);

        assertEquals(List.of("B2", "A1", "B1", "A2"), TestFiles.pageTexts(output));
    }

    @Test
    void leavesOutPagesRemovedFromTheList() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2", "A3"));
        Path output = outputs.resolve("without-second.pdf");

        composer.compose(List.of(page(a, 0), page(a, 2)), registry, PageSize.ORIGINAL, output);

        assertEquals(List.of("A1", "A3"), TestFiles.pageTexts(output));
    }

    @Test
    void allowsTheSamePageMoreThanOnce() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2"));
        Path output = outputs.resolve("duplicated.pdf");

        composer.compose(List.of(page(a, 1), page(a, 1)), registry, PageSize.ORIGINAL, output);

        assertEquals(List.of("A2", "A2"), TestFiles.pageTexts(output));
    }

    @Test
    void appliesRotationToPdfPages() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2", "A3", "A4"));
        Path output = outputs.resolve("rotated.pdf");

        composer.compose(List.of(
                        page(a, 0),
                        page(a, 1).rotatedBy(90),
                        page(a, 2).rotatedBy(180),
                        page(a, 3).rotatedBy(-90)),
                registry, PageSize.ORIGINAL, output);

        assertEquals(List.of(0, 90, 180, 270), rotations(output));
    }

    @Test
    void addsRotationToTheRotationTheSourcePageAlreadyHas() throws Exception {
        Path source = TestFiles.pdf(inputs, "already-rotated.pdf", "A1");
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            document.getPage(0).setRotation(90);
            document.save(source.toFile());
        }
        SourceDocument a = registry.open(source);
        Path output = outputs.resolve("more-rotated.pdf");

        composer.compose(List.of(page(a, 0).rotatedBy(90)), registry, PageSize.ORIGINAL, output);

        assertEquals(List.of(180), rotations(output));
    }

    @Test
    void mixesImagesAndPdfPages() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        SourceDocument b = registry.open(TestFiles.pdf(inputs, "b.pdf", "B1"));
        Path photo = TestFiles.image(inputs, "photo.jpg", "jpg", 60, 30);
        Path scan = TestFiles.image(inputs, "scan.png", "png", 30, 60);
        Path output = outputs.resolve("mixed.pdf");

        composer.compose(List.of(page(a, 0), PageItem.image(photo), page(b, 0), PageItem.image(scan)),
                registry, PageSize.ORIGINAL, output);

        assertEquals(4, TestFiles.pageTexts(output).size());
        assertEquals("A1", TestFiles.pageTexts(output).get(0));
        assertEquals("B1", TestFiles.pageTexts(output).get(2));
        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            assertEquals(60, result.getPage(1).getMediaBox().getWidth(), DELTA);
            assertEquals(30, result.getPage(1).getMediaBox().getHeight(), DELTA);
            assertEquals(30, result.getPage(3).getMediaBox().getWidth(), DELTA);
            assertEquals(60, result.getPage(3).getMediaBox().getHeight(), DELTA);
        }
    }

    @Test
    void a4PortraitAndLandscapeAreChosenPerImage() throws Exception {
        Path landscape = TestFiles.image(inputs, "wide.png", "png", 200, 100);
        Path portrait = TestFiles.image(inputs, "tall.png", "png", 100, 200);
        Path output = outputs.resolve("a4.pdf");

        composer.compose(List.of(PageItem.image(landscape), PageItem.image(portrait)),
                registry, PageSize.A4, output);

        try (PDDocument result = Loader.loadPDF(output.toFile())) {
            PDPage first = result.getPage(0);
            PDPage second = result.getPage(1);
            assertEquals(841.89, first.getMediaBox().getWidth(), DELTA);
            assertEquals(595.28, first.getMediaBox().getHeight(), DELTA);
            assertEquals(595.28, second.getMediaBox().getWidth(), DELTA);
            assertEquals(841.89, second.getMediaBox().getHeight(), DELTA);
        }
    }

    @Test
    void appliesRotationToImagePages() throws Exception {
        Path photo = TestFiles.image(inputs, "photo.png", "png", 60, 30);
        Path output = outputs.resolve("rotated-image.pdf");

        composer.compose(List.of(PageItem.image(photo).rotatedBy(90)), registry, PageSize.ORIGINAL, output);

        assertEquals(List.of(90), rotations(output));
    }

    @Test
    void rejectsTextFileWithImageExtensionAndLeavesNothingBehind() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        Path fake = TestFiles.fakeFile(inputs, "fake.png", "plain text");
        Path output = outputs.resolve("never.pdf");

        assertThrows(UnsupportedFileException.class, () -> composer.compose(
                List.of(page(a, 0), PageItem.image(fake)), registry, PageSize.ORIGINAL, output));

        assertFalse(Files.exists(output));
        assertEquals(Set.of(), fileNames(outputs));
    }

    @Test
    void neverModifiesTheOriginalFiles() throws Exception {
        Path pdfA = TestFiles.pdf(inputs, "a.pdf", "A1", "A2");
        Path pdfB = TestFiles.pdf(inputs, "b.pdf", "B1");
        Path photo = TestFiles.image(inputs, "photo.jpg", "jpg", 60, 30);
        String hashA = TestFiles.sha256(pdfA);
        String hashB = TestFiles.sha256(pdfB);
        String hashPhoto = TestFiles.sha256(photo);
        SourceDocument a = registry.open(pdfA);
        SourceDocument b = registry.open(pdfB);

        composer.compose(List.of(page(b, 0).rotatedBy(90), PageItem.image(photo), page(a, 1), page(a, 0)),
                registry, PageSize.A4, outputs.resolve("result.pdf"));
        registry.close();

        assertEquals(hashA, TestFiles.sha256(pdfA));
        assertEquals(hashB, TestFiles.sha256(pdfB));
        assertEquals(hashPhoto, TestFiles.sha256(photo));
    }

    @Test
    void leavesOnlyTheOutputFileInTheFolder() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));

        composer.compose(List.of(page(a, 0)), registry, PageSize.ORIGINAL, outputs.resolve("result.pdf"));

        assertEquals(Set.of("result.pdf"), fileNames(outputs));
    }

    @Test
    void replacesAnExistingOutputFileCompletely() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        Path output = TestFiles.pdf(outputs, "result.pdf", "OLD1", "OLD2", "OLD3");

        composer.compose(List.of(page(a, 0)), registry, PageSize.ORIGINAL, output);

        assertEquals(List.of("A1"), TestFiles.pageTexts(output));
        assertEquals(Set.of("result.pdf"), fileNames(outputs));
    }

    @Test
    void failureKeepsThePreviousOutputIntact() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        Path fake = TestFiles.fakeFile(inputs, "fake.jpg", "plain text");
        Path output = TestFiles.pdf(outputs, "result.pdf", "KEEP");
        String hashBefore = TestFiles.sha256(output);

        assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(page(a, 0), PageItem.image(fake)), registry, PageSize.ORIGINAL, output));

        assertEquals(hashBefore, TestFiles.sha256(output));
        assertEquals(Set.of("result.pdf"), fileNames(outputs));
    }

    @Test
    void refusesToOverwriteASourcePdf() throws Exception {
        Path source = TestFiles.pdf(inputs, "a.pdf", "A1", "A2");
        String hashBefore = TestFiles.sha256(source);
        SourceDocument a = registry.open(source);

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(page(a, 0)), registry, PageSize.ORIGINAL, source));

        assertEquals("Não é permitido sobrescrever um arquivo de origem. Escolha outro nome para o PDF.",
                error.getMessage());
        assertEquals(hashBefore, TestFiles.sha256(source));
    }

    @Test
    void refusesToOverwriteASourceImage() throws Exception {
        Path photo = TestFiles.image(inputs, "photo.png", "png", 20, 20);
        String hashBefore = TestFiles.sha256(photo);

        assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(PageItem.image(photo)), registry, PageSize.ORIGINAL, photo));

        assertEquals(hashBefore, TestFiles.sha256(photo));
    }

    @Test
    void refusesEmptyList() {
        PdfLocalException error = assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(), registry, PageSize.ORIGINAL, outputs.resolve("empty.pdf")));

        assertEquals("Não há páginas para salvar.", error.getMessage());
    }

    @Test
    void refusesPagesFromUnknownSourceOrOutOfRange() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        Path output = outputs.resolve("never.pdf");

        assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(PageItem.pdfPage(java.util.UUID.randomUUID(), 0)), registry, PageSize.ORIGINAL, output));
        assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(page(a, 1)), registry, PageSize.ORIGINAL, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void reportsMissingOutputFolder() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1"));
        Path output = directory.resolve("does-not-exist").resolve("result.pdf");

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> composer.compose(
                List.of(page(a, 0)), registry, PageSize.ORIGINAL, output));

        assertTrue(error.getMessage().startsWith("Não foi possível salvar o PDF"));
    }

    @Test
    void keepsSourceDocumentsOpenForTheCaller() throws Exception {
        SourceDocument a = registry.open(TestFiles.pdf(inputs, "a.pdf", "A1", "A2"));

        composer.compose(List.of(page(a, 0)), registry, PageSize.ORIGINAL, outputs.resolve("first.pdf"));
        composer.compose(List.of(page(a, 1)), registry, PageSize.ORIGINAL, outputs.resolve("second.pdf"));

        assertFalse(registry.document(a.id()).getDocument().isClosed());
        assertEquals(List.of("A2"), TestFiles.pageTexts(outputs.resolve("second.pdf")));
    }

    private static PageItem page(SourceDocument source, int index) {
        return PageItem.pdfPage(source.id(), index);
    }

    private static List<Integer> rotations(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return java.util.stream.IntStream.range(0, document.getNumberOfPages())
                    .mapToObj(i -> document.getPage(i).getRotation())
                    .toList();
        }
    }

    private static Set<String> fileNames(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
