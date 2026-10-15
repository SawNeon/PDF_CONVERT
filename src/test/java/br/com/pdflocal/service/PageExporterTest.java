package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PageExporterTest {

    private final DocumentComposer composer = new DocumentComposer();
    private final PageExporter exporter = new PageExporter(composer);
    private final SourceRegistry registry = new SourceRegistry();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void singlePageBecomesOneJpegWithTheExpectedSize() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        Path output = directory.resolve("page.jpg");

        SaveResult result = exporter.export(pages(source, 1), registry, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.JPEG, 100), output, SaveListener.NONE);

        byte[] bytes = Files.readAllBytes(output);
        assertEquals(1, result.pages());
        assertEquals(bytes.length, result.outputBytes());
        assertEquals((byte) 0xFF, bytes[0]);
        assertEquals((byte) 0xD8, bytes[1]);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(612 * 100 / 72.0, image.getWidth(), 1.5);
        assertEquals(792 * 100 / 72.0, image.getHeight(), 1.5);
        assertTrue(hasInk(image, 90, 120, 160, 160));
    }

    @Test
    void singlePageCanBeExportedAsPng() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1"));
        Path output = directory.resolve("page.png");

        exporter.export(pages(source, 1), registry, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.PNG, 72), output, SaveListener.NONE);

        byte[] bytes = Files.readAllBytes(output);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals(612, ImageIO.read(new ByteArrayInputStream(bytes)).getWidth());
    }

    @Test
    void manyPagesBecomeAZipWithGeneratedNamesOnly() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "paciente-joao-silva.pdf", "A1", "A2", "A3"));
        Path output = directory.resolve("pages.zip");
        List<Integer> progress = new ArrayList<>();

        exporter.export(pages(source, 3), registry, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.PNG, 72), output, new SaveListener() {
                    @Override
                    public void onPage(int done, int total) {
                    }

                    @Override
                    public void onTransform(int done, int total) {
                        progress.add(done);
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });

        try (ZipFile zip = new ZipFile(output.toFile())) {
            List<String> names = zip.stream().map(ZipEntry::getName).toList();
            assertEquals(List.of("pagina-001.png", "pagina-002.png", "pagina-003.png"), names);
            for (ZipEntry entry : zip.stream().toList()) {
                assertEquals(612, ImageIO.read(zip.getInputStream(entry)).getWidth());
            }
        }
        assertTrue(progress.contains(3));
    }

    @Test
    void rotatedAndDeletedPagesAreHonoured() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1", "A2", "A3"));
        List<PageItem> items = List.of(PageItem.pdfPage(source.id(), 0).rotatedBy(90), PageItem.pdfPage(source.id(), 2));
        Path output = directory.resolve("pages.zip");

        exporter.export(items, registry, PageSize.A4, new ImageExportOptions(ImageExportOptions.Format.PNG, 72),
                output, SaveListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertEquals(2, zip.size());
            BufferedImage first = ImageIO.read(zip.getInputStream(zip.getEntry("pagina-001.png")));
            BufferedImage second = ImageIO.read(zip.getInputStream(zip.getEntry("pagina-002.png")));
            assertEquals(792, first.getWidth());
            assertEquals(612, first.getHeight());
            assertEquals(612, second.getWidth());
        }
    }

    @Test
    void imagesInTheListAreExportedToo() throws Exception {
        Path photo = TestFiles.image(directory, "photo.png", "png", 300, 200);
        Path output = directory.resolve("out.png");

        exporter.export(List.of(PageItem.image(photo)), registry, PageSize.ORIGINAL,
                new ImageExportOptions(ImageExportOptions.Format.PNG, 72), output, SaveListener.NONE);

        BufferedImage image = ImageIO.read(output.toFile());
        assertEquals(300, image.getWidth(), 2);
        assertEquals(200, image.getHeight(), 2);
    }

    @Test
    void cancellingLeavesNoFilesBehind() throws Exception {
        SourceDocument source = registry.open(TestFiles.pdf(directory, "a.pdf", "A1", "A2", "A3"));
        Path outputs = Files.createDirectory(directory.resolve("outputs"));
        Path output = outputs.resolve("pages.zip");
        SaveListener cancelAfterFirst = new SaveListener() {
            private boolean cancelled;

            @Override
            public void onPage(int done, int total) {
            }

            @Override
            public void onTransform(int done, int total) {
                cancelled = done >= 1;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };

        assertThrows(SaveCancelledException.class, () -> exporter.export(pages(source, 3), registry, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.PNG, 72), output, cancelAfterFirst));

        try (var files = Files.list(outputs)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void sourceFilesCannotBeOverwritten() throws Exception {
        Path pdf = TestFiles.pdf(directory, "a.pdf", "A1");
        SourceDocument source = registry.open(pdf);

        assertThrows(PdfLocalException.class, () -> exporter.export(pages(source, 1), registry, PageSize.A4,
                new ImageExportOptions(ImageExportOptions.Format.JPEG, 72), pdf, SaveListener.NONE));
        assertFalse(Files.readString(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).isEmpty());
        assertTrue(Files.readString(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @Test
    void optionsRejectOutOfRangeResolution() {
        assertThrows(IllegalArgumentException.class, () -> new ImageExportOptions(ImageExportOptions.Format.JPEG, 10));
        assertThrows(IllegalArgumentException.class, () -> new ImageExportOptions(ImageExportOptions.Format.PNG, 900));
    }

    private static List<PageItem> pages(SourceDocument source, int count) {
        List<PageItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(PageItem.pdfPage(source.id(), i));
        }
        return items;
    }

    private static boolean hasInk(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < Math.min(image.getHeight(), y1); y++) {
            for (int x = x0; x < Math.min(image.getWidth(), x1); x++) {
                Color color = new Color(image.getRGB(x, y));
                if ((color.getRed() + color.getGreen() + color.getBlue()) / 3 < 140) {
                    return true;
                }
            }
        }
        return false;
    }
}
