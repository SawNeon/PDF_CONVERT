package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class DocumentComposerCompressionTest {

    private static final int WIDTH = 2000;
    private static final int HEIGHT = 2800;

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
    void compressedOutputIsSmallerAndReportsItsSize() throws Exception {
        SourceDocument source = registry.open(TestFiles.scannedPdf(inputs, "scan.pdf", 2, WIDTH, HEIGHT, 0.9f, false));
        List<PageItem> pages = List.of(PageItem.pdfPage(source.id(), 0), PageItem.pdfPage(source.id(), 1));
        Path plain = outputs.resolve("plain.pdf");
        Path small = outputs.resolve("small.pdf");

        SaveResult plainResult = composer.compose(pages, registry, PageSize.A4, plain);
        SaveResult smallResult = composer.compose(
                pages, registry, PageSize.A4, CompressionLevel.MEDIUM, small, SaveListener.NONE);

        assertEquals(Files.size(plain), plainResult.outputBytes());
        assertEquals(Files.size(small), smallResult.outputBytes());
        assertTrue(smallResult.outputBytes() < plainResult.outputBytes() / 2);
        assertEquals(2, smallResult.pages());
        assertEquals(2, smallResult.compression().replaced());
        assertEquals(0, plainResult.compression().images());
    }

    @Test
    void compressionNeverTouchesTheOriginalFile() throws Exception {
        Path scan = TestFiles.scannedPdf(inputs, "scan.pdf", 1, WIDTH, HEIGHT, 0.9f, false);
        String hash = TestFiles.sha256(scan);
        SourceDocument source = registry.open(scan);

        composer.compose(List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4,
                CompressionLevel.STRONG, outputs.resolve("small.pdf"), SaveListener.NONE);
        registry.close();

        assertEquals(hash, TestFiles.sha256(scan));
    }

    @Test
    void compressionDoesNotAlterTheSourceDocumentKeptOpenInMemory() throws Exception {
        SourceDocument source = registry.open(TestFiles.scannedPdf(inputs, "scan.pdf", 1, WIDTH, HEIGHT, 0.9f, false));
        PageItem page = PageItem.pdfPage(source.id(), 0);

        composer.compose(List.of(page), registry, PageSize.A4, CompressionLevel.STRONG,
                outputs.resolve("first.pdf"), SaveListener.NONE);

        PDImageXObject afterFirst = TestFiles.imagesOf(registry.document(source.id())).get(0);
        assertEquals(WIDTH, afterFirst.getWidth());

        composer.compose(List.of(page), registry, PageSize.A4, outputs.resolve("second.pdf"));
        try (PDDocument second = Loader.loadPDF(outputs.resolve("second.pdf").toFile())) {
            assertEquals(WIDTH, TestFiles.imagesOf(second).get(0).getWidth());
        }
    }

    @Test
    void savingTwiceWithCompressionGivesTheSameResult() throws Exception {
        SourceDocument source = registry.open(TestFiles.scannedPdf(inputs, "scan.pdf", 1, WIDTH, HEIGHT, 0.9f, false));
        List<PageItem> pages = List.of(PageItem.pdfPage(source.id(), 0));

        SaveResult first = composer.compose(pages, registry, PageSize.A4, CompressionLevel.MEDIUM,
                outputs.resolve("a.pdf"), SaveListener.NONE);
        SaveResult second = composer.compose(pages, registry, PageSize.A4, CompressionLevel.MEDIUM,
                outputs.resolve("b.pdf"), SaveListener.NONE);

        assertEquals(first.outputBytes(), second.outputBytes());
    }

    @Test
    void imageFilesAreCompressedToo() throws Exception {
        Path png = TestFiles.largeNoisyPng(inputs, "scan.png", WIDTH, HEIGHT);
        List<PageItem> pages = List.of(PageItem.image(png));

        SaveResult plain = composer.compose(pages, registry, PageSize.A4, outputs.resolve("plain.pdf"));
        SaveResult small = composer.compose(pages, registry, PageSize.A4, CompressionLevel.MEDIUM,
                outputs.resolve("small.pdf"), SaveListener.NONE);

        assertTrue(small.outputBytes() < plain.outputBytes() / 2);
        try (PDDocument document = Loader.loadPDF(outputs.resolve("small.pdf").toFile())) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(595.28, document.getPage(0).getMediaBox().getWidth(), 0.01);
        }
    }

    @Test
    void cancellingDuringCompressionLeavesNoFileBehind() throws Exception {
        SourceDocument source = registry.open(TestFiles.scannedPdf(inputs, "scan.pdf", 3, 1400, 1960, 0.9f, false));
        List<PageItem> pages = List.of(
                PageItem.pdfPage(source.id(), 0), PageItem.pdfPage(source.id(), 1), PageItem.pdfPage(source.id(), 2));
        Path output = outputs.resolve("never.pdf");
        int[] compressed = {0};
        SaveListener listener = new SaveListener() {
            @Override
            public void onPage(int done, int total) {
            }

            @Override
            public void onCompress(int done, int total) {
                compressed[0] = done;
            }

            @Override
            public boolean isCancelled() {
                return compressed[0] >= 1;
            }
        };

        assertThrows(SaveCancelledException.class, () -> composer.compose(
                pages, registry, PageSize.A4, CompressionLevel.MEDIUM, output, listener));

        assertFalse(Files.exists(output));
        assertEquals(Set.of(), fileNames(outputs));
    }

    @Test
    void noneLevelBehavesLikeTheRegularSave() throws Exception {
        SourceDocument source = registry.open(TestFiles.scannedPdf(inputs, "scan.pdf", 1, 800, 1100, 0.9f, false));
        List<PageItem> pages = List.of(PageItem.pdfPage(source.id(), 0));

        SaveResult none = composer.compose(pages, registry, PageSize.A4, CompressionLevel.NONE,
                outputs.resolve("none.pdf"), SaveListener.NONE);
        SaveResult regular = composer.compose(pages, registry, PageSize.A4, outputs.resolve("regular.pdf"));

        assertEquals(regular.outputBytes(), none.outputBytes());
        assertEquals(CompressionStats.EMPTY, none.compression());
    }

    private static Set<String> fileNames(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
