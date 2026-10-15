package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfAStepTest {

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void theValidatorRejectsAnOrdinaryPdfSoTheCheckMeansSomething() throws Exception {
        Path plain = TestFiles.pdf(directory, "plain.pdf", "A1");

        assertFalse(PreflightParser.validate(plain.toFile()).isValid());
    }

    @Test
    void aTextPdfBecomesValidPdfA1b() throws Exception {
        Path output = convert(TestFiles.pdf(directory, "text.pdf", "A1", "A2"), 2, 0);

        assertValid(output);
    }

    @Test
    void aRotatedScanBecomesValidPdfA1b() throws Exception {
        Path output = convert(TestFiles.scannedPdf(directory, "scan.pdf", 1, 1200, 1700, 0.9f, false), 1, 90);

        assertValid(output);
        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertEquals(842, document.getPage(0).getMediaBox().getWidth(), 1.0);
        }
    }

    @Test
    void pageCountAndSizeAreKeptAndTheTextBecomesAnImage() throws Exception {
        Path output = convert(TestFiles.pdf(directory, "text.pdf", "A1", "A2", "A3"), 3, 0);

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertEquals(3, document.getNumberOfPages());
            assertEquals(612, document.getPage(0).getMediaBox().getWidth(), 1.0);
            assertEquals(792, document.getPage(0).getMediaBox().getHeight(), 1.0);
        }
        assertEquals(List.of("", "", ""), TestFiles.pageTexts(output));
    }

    @Test
    void theMetadataDeclaresPartOneConformanceB() throws Exception {
        Path output = convert(TestFiles.pdf(directory, "text.pdf", "A1"), 1, 0);

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("<pdfaid:part>1</pdfaid:part>"));
            assertTrue(xmp.contains("<pdfaid:conformance>B</pdfaid:conformance>"));
            assertEquals(1, document.getDocumentCatalog().getOutputIntents().size());
        }
    }

    @Test
    void theOpenSourceStillHasItsText() throws Exception {
        Path source = TestFiles.pdf(directory, "text.pdf", "A1");
        convert(source, 1, 0);

        assertEquals(List.of("A1"), TestFiles.pageTexts(source));
    }

    private Path convert(Path source, int pages, int rotation) throws Exception {
        SourceDocument opened = registry.open(source);
        List<PageItem> items = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            PageItem item = PageItem.pdfPage(opened.id(), i);
            items.add(rotation == 0 ? item : item.rotatedBy(rotation));
        }
        Path output = directory.resolve("pdfa-" + source.getFileName());
        composer.compose(items, registry, PageSize.ORIGINAL, List.of(new PdfAStep()), output, SaveListener.NONE);
        return output;
    }

    private static void assertValid(Path file) throws Exception {
        ValidationResult result = PreflightParser.validate(file.toFile());
        String problems = result.getErrorsList().stream()
                .map(error -> error.getErrorCode() + " " + error.getDetails())
                .collect(Collectors.joining("; "));
        assertTrue(result.isValid(), problems);
    }
}
