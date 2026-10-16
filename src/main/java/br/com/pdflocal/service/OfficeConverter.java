package br.com.pdflocal.service;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OfficeConverter {

    public enum Engine {
        MICROSOFT_OFFICE,
        LIBREOFFICE
    }

    private static final Logger LOG = Logger.getLogger(OfficeConverter.class.getName());

    private final SystemProbe probe;
    private final Path libreOfficeHome;
    private final OfficeFileDetector detector = new OfficeFileDetector();

    public OfficeConverter() {
        this(new WindowsSystemProbe(), LibreOfficeProfile.defaultHome());
    }

    OfficeConverter(SystemProbe probe, Path libreOfficeHome) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.libreOfficeHome = Objects.requireNonNull(libreOfficeHome, "libreOfficeHome");
    }

    public Engine convert(Path input, Path output, SaveListener listener)
            throws PdfLocalException, SaveCancelledException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(listener, "listener");
        OfficeKind kind = detector.detect(input);
        Engine engine = engineFor(kind);
        Path directory = output.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
        }
        if (OutputFiles.isSameFile(input, output)) {
            throw new PdfLocalException(Messages.SAVE_OVERWRITES_SOURCE);
        }

        Path work = null;
        try {
            work = Files.createTempDirectory(directory, "pdflocal-");
            Path produced = engine == Engine.MICROSOFT_OFFICE
                    ? withOffice(kind, input, work, listener) : withLibreOffice(input, work, listener);
            if (!Files.isRegularFile(produced) || !OfficeRunner.looksLikePdf(produced)) {
                throw new PdfLocalException(Messages.CONVERT_FAILED);
            }
            OutputFiles.moveAtomically(produced, output);
            LOG.log(Level.INFO, "Document converted to PDF with {0}", engine);
            return engine;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to write the converted PDF: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(DocumentComposer.classify(e, OutputFiles.usableSpace(directory)), e);
        } finally {
            OutputFiles.deleteTree(work);
        }
    }

    Engine engineFor(OfficeKind kind) throws PdfLocalException {
        return choose(probe, kind, true);
    }

    static Engine choose(SystemProbe probe, OfficeKind kind, boolean allowLibreOffice) throws PdfLocalException {
        boolean installed = probe.officeInstalled(kind);
        if (installed && !probe.officeRunning(kind)) {
            return Engine.MICROSOFT_OFFICE;
        }
        if (allowLibreOffice && probe.libreOffice().isPresent()) {
            return Engine.LIBREOFFICE;
        }
        throw new PdfLocalException(installed ? Messages.CONVERT_APP_RUNNING : Messages.CONVERT_NO_ENGINE);
    }

    private static Path withOffice(OfficeKind kind, Path input, Path work, SaveListener listener)
            throws PdfLocalException, SaveCancelledException {
        Path produced = work.resolve("saida.pdf");
        String action = switch (kind) {
            case WORD -> "WordToPdf";
            case EXCEL -> "ExcelToPdf";
            case POWERPOINT -> "PowerPointToPdf";
        };
        OfficeRunner.runScript(kind, action, input, produced, null, listener);
        return produced;
    }

    private Path withLibreOffice(Path input, Path work, SaveListener listener)
            throws PdfLocalException, SaveCancelledException, IOException {
        Path soffice = probe.libreOffice().orElseThrow(() -> new PdfLocalException(Messages.CONVERT_NO_ENGINE));
        return LibreOfficeRunner.convert(soffice, libreOfficeHome, input, List.of("--convert-to", "pdf"), "pdf", work,
                listener);
    }
}
