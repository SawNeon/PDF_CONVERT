package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfToOfficeTest {

    private static final SystemProbe REAL = new WindowsSystemProbe();

    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    private Path home;

    @BeforeEach
    void createShortHome() throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(local, "Temp");
        home = Files.createTempDirectory(base, "pp");
    }

    @AfterEach
    void cleanUp() {
        registry.close();
        OutputFiles.deleteTree(home);
    }

    @Test
    void powerPointNeverFallsBackToLibreOfficeWhenItIsOpen() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PdfToOffice converter = new PdfToOffice(new Probe(true, true, true), home);

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> converter.toPowerPoint(
                List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4, directory.resolve("out.pptx"),
                SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_APP_RUNNING), error.getMessage());
    }

    @Test
    void withoutAnyEngineTheUserIsTold() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        PdfToOffice converter = new PdfToOffice(new Probe(false, false, false), home);

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> converter.toWord(
                List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4, directory.resolve("out.docx"),
                SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_NO_ENGINE), error.getMessage());
    }

    @Test
    void zipEntriesWithUnexpectedNamesAreRefused() throws Exception {
        Path evil = zip("evil.zip", "pagina-001.png", "../fora.png");
        Path target = Files.createDirectory(directory.resolve("img"));

        assertThrows(PdfLocalException.class, () -> PdfToOffice.extractImages(evil, target));
        assertTrue(Files.notExists(directory.resolve("fora.png")));
    }

    @Test
    void onlyGeneratedPageNamesAreExtractedInOrder() throws Exception {
        Path good = zip("good.zip", "pagina-002.png", "pagina-001.png");
        Path target = Files.createDirectory(directory.resolve("good"));

        List<Path> extracted = PdfToOffice.extractImages(good, target);

        assertEquals(List.of("pagina-001.png", "pagina-002.png"),
                extracted.stream().map(path -> path.getFileName().toString()).toList());
    }

    @Test
    void absolutePathsAndSubfoldersInTheZipAreRefused() throws Exception {
        for (String name : List.of("/etc/pagina-001.png", "sub/pagina-001.png", "pagina-001.png.exe", "C:/x.png")) {
            Path bad = zip("bad" + Math.abs(name.hashCode()) + ".zip", name);
            Path target = Files.createDirectory(directory.resolve("t" + Math.abs(name.hashCode())));

            assertThrows(PdfLocalException.class, () -> PdfToOffice.extractImages(bad, target), name);
        }
    }

    @Test
    @Tag("office")
    void libreOfficeConvertsAPdfIntoAnEditableDocument() throws Exception {
        assumeTrue(REAL.libreOffice().isPresent());
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "EFGH5678"));
        Path output = directory.resolve("saida-lo.docx");

        OfficeConverter.Engine engine = new PdfToOffice(new Probe(false, false, true) {
            @Override
            public Optional<Path> libreOffice() {
                return REAL.libreOffice();
            }
        }, home).toWord(List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4, output, SaveListener.NONE);

        assertEquals(OfficeConverter.Engine.LIBREOFFICE, engine);
        assertTrue(documentText(output).contains("EFGH5678"));
        assertNoLeftovers();
    }

    @Test
    @Tag("office")
    void powerPointBuildsOneSlideWithAnImagePerPage() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.POWERPOINT) && !REAL.officeRunning(OfficeKind.POWERPOINT));
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "P1", "P2", "P3"));
        List<PageItem> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            items.add(PageItem.pdfPage(source.id(), i));
        }
        Path output = directory.resolve("saida.pptx");

        new PdfToOffice(REAL, home).toPowerPoint(items, registry, PageSize.A4, output, SaveListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertEquals(3, names.stream().filter(name -> name.matches("ppt/slides/slide\\d+\\.xml")).count());
            assertEquals(3, names.stream().filter(name -> name.startsWith("ppt/media/")).count());
        }
        assertNoLeftovers();
    }

    @Test
    @Tag("office")
    void powerPointAcceptsASinglePage() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.POWERPOINT) && !REAL.officeRunning(OfficeKind.POWERPOINT));
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "UNICA"));
        Path output = directory.resolve("unica.pptx");

        new PdfToOffice(REAL, home).toPowerPoint(List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4,
                output, SaveListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertEquals(1, zip.stream().filter(entry -> entry.getName().matches("ppt/slides/slide\\d+\\.xml")).count());
        }
    }

    private String documentText(Path docx) throws IOException {
        try (ZipFile zip = new ZipFile(docx.toFile())) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            String xml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            return xml.replaceAll("<[^>]+>", "");
        }
    }

    private void assertNoLeftovers() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            List<String> leftovers = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("pdflocal-")).collect(Collectors.toList());
            assertTrue(leftovers.isEmpty(), "temporary work folder was not removed: " + leftovers);
        }
    }

    private Path zip(String name, String... entries) throws IOException {
        Path file = directory.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
                zip.closeEntry();
            }
        }
        return file;
    }

    private static class Probe implements SystemProbe {

        private final boolean installed;
        private final boolean running;
        private final boolean libreOffice;

        Probe(boolean installed, boolean running, boolean libreOffice) {
            this.installed = installed;
            this.running = running;
            this.libreOffice = libreOffice;
        }

        @Override
        public boolean officeInstalled(OfficeKind app) {
            return installed;
        }

        @Override
        public boolean officeRunning(OfficeKind app) {
            return running;
        }

        @Override
        public Optional<Path> libreOffice() {
            return libreOffice ? Optional.of(Path.of("soffice.exe")) : Optional.empty();
        }

        @Override
        public Optional<Path> browser() {
            return Optional.empty();
        }
    }
}
