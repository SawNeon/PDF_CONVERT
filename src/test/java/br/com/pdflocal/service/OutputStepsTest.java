package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class OutputStepsTest {

    private final DocumentComposer composer = new DocumentComposer();
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void savedPdfDropsEmbeddedPageThumbnails() throws Exception {
        Path source = pdfWithThumbnails("with-thumb.pdf", "A1", "A2");
        SourceDocument document = registry.open(source);
        Path output = directory.resolve("out.pdf");

        composer.compose(List.of(PageItem.pdfPage(document.id(), 0), PageItem.pdfPage(document.id(), 1)),
                registry, PageSize.ORIGINAL, output);

        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            for (int i = 0; i < saved.getNumberOfPages(); i++) {
                assertFalse(saved.getPage(i).getCOSObject().containsKey(COSName.THUMB), "page " + i);
            }
        }
    }

    @Test
    void savedPdfDoesNotInheritTheDocumentMetadataOfTheSources() throws Exception {
        Path plain = TestFiles.pdf(directory, "plain.pdf", "A1");
        Path source = directory.resolve("with-metadata.pdf");
        try (PDDocument document = Loader.loadPDF(plain.toFile())) {
            document.getDocumentInformation().setAuthor("Maria Souza");
            document.getDocumentInformation().setTitle("Laudo do paciente");
            document.save(source.toFile());
        }
        SourceDocument opened = registry.open(source);
        Path output = directory.resolve("out.pdf");

        composer.compose(List.of(PageItem.pdfPage(opened.id(), 0)), registry, PageSize.ORIGINAL, output);

        try (PDDocument saved = Loader.loadPDF(output.toFile())) {
            assertEquals(null, saved.getDocumentInformation().getAuthor());
            assertEquals(null, saved.getDocumentInformation().getTitle());
        }
    }

    @Test
    void thumbnailsOfTheOpenSourceAreLeftUntouched() throws Exception {
        Path source = pdfWithThumbnails("with-thumb.pdf", "A1");
        SourceDocument document = registry.open(source);

        composer.compose(List.of(PageItem.pdfPage(document.id(), 0)), registry, PageSize.ORIGINAL,
                directory.resolve("out.pdf"));

        assertTrue(registry.document(document.id()).getPage(0).getCOSObject().containsKey(COSName.THUMB));
        try (PDDocument original = Loader.loadPDF(source.toFile())) {
            assertNotNull(original.getPage(0).getCOSObject().getDictionaryObject(COSName.THUMB));
        }
    }

    @Test
    void customStepsRunInOrderAfterThePagesAreBuilt() throws Exception {
        SourceDocument document = registry.open(TestFiles.pdf(directory, "a.pdf", "A1", "A2", "A3"));
        List<String> events = new ArrayList<>();
        OutputStep first = (pdf, listener) -> events.add("first:" + pdf.getNumberOfPages());
        OutputStep second = (pdf, listener) -> events.add("second:" + pdf.getNumberOfPages());

        composer.compose(
                List.of(PageItem.pdfPage(document.id(), 0), PageItem.pdfPage(document.id(), 2)),
                registry, PageSize.ORIGINAL, List.of(first, second), directory.resolve("out.pdf"), SaveListener.NONE);

        assertEquals(List.of("first:2", "second:2"), events);
    }

    @Test
    void aFailingStepLeavesNoOutputBehind() throws Exception {
        SourceDocument document = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        Path output = directory.resolve("out.pdf");
        OutputStep failing = (pdf, listener) -> {
            throw new IOException("boom");
        };

        try {
            composer.compose(List.of(PageItem.pdfPage(document.id(), 0)), registry, PageSize.ORIGINAL,
                    List.of(failing), output, SaveListener.NONE);
        } catch (Exception expected) {
            assertTrue(Files.notExists(output));
            try (var files = Files.list(directory)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
            return;
        }
        throw new AssertionError("expected the save to fail");
    }

    private Path pdfWithThumbnails(String name, String... labels) throws IOException {
        Path plain = TestFiles.pdf(directory, "plain-" + name, labels);
        Path result = directory.resolve(name);
        try (PDDocument document = Loader.loadPDF(plain.toFile())) {
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                COSStream thumbnail = document.getDocument().createCOSStream();
                try (OutputStream out = thumbnail.createRawOutputStream()) {
                    out.write(new byte[64]);
                }
                document.getPage(i).getCOSObject().setItem(COSName.THUMB, thumbnail);
            }
            document.save(result.toFile());
        }
        return result;
    }
}
