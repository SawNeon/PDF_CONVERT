package br.com.pdflocal.service;

import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class PdfToOffice {

    static final int SLIDE_DPI = 150;
    static final int MAX_IMAGES = 2_000;
    static final long MAX_IMAGE_BYTES = 200L * 1024 * 1024;

    private static final Pattern IMAGE_NAME = Pattern.compile("pagina-\\d{3,}\\.png");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};
    private static final Logger LOG = Logger.getLogger(PdfToOffice.class.getName());

    private final DocumentComposer composer = new DocumentComposer();
    private final PageExporter exporter = new PageExporter(composer);
    private final OfficeFileDetector detector = new OfficeFileDetector();
    private final SystemProbe probe;
    private final Path libreOfficeHome;

    public PdfToOffice() {
        this(new WindowsSystemProbe(), LibreOfficeProfile.defaultHome());
    }

    PdfToOffice(SystemProbe probe, Path libreOfficeHome) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.libreOfficeHome = Objects.requireNonNull(libreOfficeHome, "libreOfficeHome");
    }

    public OfficeConverter.Engine toWord(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize,
                                         Path output, SaveListener listener)
            throws PdfLocalException, SaveCancelledException {
        Path soffice = probe.libreOffice().orElseThrow(() -> new PdfLocalException(Messages.CONVERT_NO_ENGINE));
        Path directory = requireFolder(output);
        Path work = null;
        try {
            work = Files.createTempDirectory(directory, "pdflocal-");
            Path assembled = work.resolve("entrada.pdf");
            composer.compose(items, registry, imagePageSize, List.of(), assembled, listener);
            Path produced = LibreOfficeRunner.convert(soffice, libreOfficeHome, assembled,
                    List.of("--infilter=writer_pdf_import", "--convert-to", "docx:MS Word 2007 XML"), "docx", work,
                    listener);
            finish(produced, output, OfficeKind.WORD);
            LOG.log(Level.INFO, "PDF converted to Word with LibreOffice");
            return OfficeConverter.Engine.LIBREOFFICE;
        } catch (IOException e) {
            throw failure(e, directory);
        } finally {
            OutputFiles.deleteTree(work);
        }
    }

    public void toExcel(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize, Path output,
                        SaveListener listener) throws PdfLocalException, SaveCancelledException {
        Path directory = requireFolder(output);
        Path work = null;
        try {
            work = Files.createTempDirectory(directory, "pdflocal-");
            Path assembled = work.resolve("entrada.pdf");
            composer.compose(items, registry, imagePageSize, List.of(), assembled, listener);
            List<XlsxWriter.Sheet> sheets;
            try (PDDocument document = PdfLoader.open(assembled, null)) {
                sheets = new TableExtractor().extract(document, listener);
            }
            if (sheets.isEmpty()) {
                throw new PdfLocalException(Messages.CONVERT_NO_TEXT);
            }
            Path produced = work.resolve("saida.xlsx");
            XlsxWriter.write(produced, sheets);
            finish(produced, output, OfficeKind.EXCEL);
            LOG.log(Level.INFO, "PDF converted to Excel with {0} sheets", sheets.size());
        } catch (IOException e) {
            throw failure(e, directory);
        } finally {
            OutputFiles.deleteTree(work);
        }
    }

    public void toPowerPoint(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize, Path output,
                             SaveListener listener) throws PdfLocalException, SaveCancelledException {
        OfficeConverter.choose(probe, OfficeKind.POWERPOINT, false);
        Path directory = requireFolder(output);
        Path work = null;
        try {
            work = Files.createTempDirectory(directory, "pdflocal-");
            Path images = Files.createDirectory(work.resolve("img"));
            Path exported = work.resolve("paginas.bin");
            exporter.export(items, registry, imagePageSize,
                    new ImageExportOptions(ImageExportOptions.Format.PNG, SLIDE_DPI), exported, listener);
            List<Path> slides = items.size() == 1 ? singleImage(exported, images) : extractImages(exported, images);
            Path list = work.resolve("lista.txt");
            List<String> lines = new ArrayList<>();
            slides.forEach(slide -> lines.add(slide.toAbsolutePath().toString()));
            Files.write(list, lines, StandardCharsets.UTF_8);
            Path produced = work.resolve("saida.pptx");
            OfficeRunner.runScript(OfficeKind.POWERPOINT, "ImagesToPowerPoint", null, produced, list, listener);
            finish(produced, output, OfficeKind.POWERPOINT);
            LOG.log(Level.INFO, "PDF converted to PowerPoint");
        } catch (IOException e) {
            throw failure(e, directory);
        } finally {
            OutputFiles.deleteTree(work);
        }
    }

    static List<Path> extractImages(Path zipFile, Path targetDirectory) throws IOException, PdfLocalException {
        List<Path> extracted = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            if (zip.size() > MAX_IMAGES) {
                throw new PdfLocalException(Messages.CONVERT_FAILED);
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !IMAGE_NAME.matcher(entry.getName()).matches()) {
                    throw new PdfLocalException(Messages.CONVERT_FAILED);
                }
                Path target = targetDirectory.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDirectory.normalize())) {
                    throw new PdfLocalException(Messages.CONVERT_FAILED);
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] data = in.readNBytes((int) MAX_IMAGE_BYTES + 1);
                    if (data.length > MAX_IMAGE_BYTES) {
                        throw new PdfLocalException(Messages.CONVERT_FAILED);
                    }
                    Files.write(target, data);
                }
                extracted.add(target);
            }
        }
        extracted.sort(null);
        return extracted;
    }

    private static List<Path> singleImage(Path exported, Path targetDirectory) throws IOException, PdfLocalException {
        byte[] head;
        try (InputStream in = Files.newInputStream(exported)) {
            head = in.readNBytes(PNG_SIGNATURE.length);
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (head.length < PNG_SIGNATURE.length || head[i] != PNG_SIGNATURE[i]) {
                throw new PdfLocalException(Messages.CONVERT_FAILED);
            }
        }
        Path target = targetDirectory.resolve("pagina-001.png");
        Files.copy(exported, target, StandardCopyOption.REPLACE_EXISTING);
        return List.of(target);
    }

    private void finish(Path produced, Path output, OfficeKind expected) throws PdfLocalException, IOException {
        if (!Files.isRegularFile(produced) || detector.detect(produced) != expected) {
            throw new PdfLocalException(Messages.CONVERT_FAILED);
        }
        OutputFiles.moveAtomically(produced, output);
    }

    private static Path requireFolder(Path output) throws PdfLocalException {
        Path directory = output.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
        }
        return directory;
    }

    private static PdfLocalException failure(IOException error, Path directory) {
        LOG.log(Level.WARNING, "Failed to convert the PDF: {0}", error.getClass().getSimpleName());
        return new PdfLocalException(DocumentComposer.classify(error, OutputFiles.usableSpace(directory)), error);
    }
}
