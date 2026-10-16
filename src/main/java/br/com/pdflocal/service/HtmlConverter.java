package br.com.pdflocal.service;

import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HtmlConverter {

    static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private static final int BINARY_CHECK_BYTES = 4096;
    private static final Logger LOG = Logger.getLogger(HtmlConverter.class.getName());

    private final SystemProbe probe;
    private final Path home;

    public HtmlConverter() {
        this(new WindowsSystemProbe(), LibreOfficeProfile.defaultHome().resolveSibling("html"));
    }

    HtmlConverter(SystemProbe probe, Path home) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.home = Objects.requireNonNull(home, "home");
    }

    public void convert(Path input, Path output, SaveListener listener)
            throws PdfLocalException, SaveCancelledException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(listener, "listener");
        requireHtml(input);
        Path browser = probe.browser().orElseThrow(() -> new PdfLocalException(Messages.CONVERT_NO_ENGINE));
        Path directory = output.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
        }
        if (OutputFiles.isSameFile(input, output)) {
            throw new PdfLocalException(Messages.SAVE_OVERWRITES_SOURCE);
        }

        Path work = null;
        Path temporary = null;
        try {
            Files.createDirectories(home);
            work = Files.createTempDirectory(home, "h");
            Path printed = work.resolve("saida.pdf");
            Path page = work.resolve("pagina.html");
            Files.write(page, HtmlPolicy.apply(Files.readAllBytes(input), baseOf(input)));
            int code = ExternalProcess.run(command(browser, work, printed, page), Map.of(), work, TIMEOUT, listener);
            if (code == ExternalProcess.TIMED_OUT) {
                throw new PdfLocalException(Messages.CONVERT_TIMEOUT);
            }
            if (code != 0 || !Files.isRegularFile(printed) || !OfficeRunner.looksLikePdf(printed)) {
                LOG.log(Level.WARNING, "HTML conversion failed with code {0}", code);
                throw new PdfLocalException(Messages.CONVERT_FAILED);
            }
            temporary = Files.createTempFile(directory, "pdflocal-", ".tmp");
            Files.copy(printed, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            OutputFiles.moveAtomically(temporary, output);
            temporary = null;
            LOG.log(Level.INFO, "HTML converted to PDF");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to convert the HTML: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(DocumentComposer.classify(e, OutputFiles.usableSpace(directory)), e);
        } finally {
            OutputFiles.deleteQuietly(temporary);
            OutputFiles.deleteTree(work);
        }
    }

    static List<String> command(Path browser, Path work, Path printed, Path input) {
        return List.of(browser.toString(), "--headless=new", "--disable-gpu", "--no-first-run",
                "--no-default-browser-check", "--disable-extensions", "--disable-background-networking",
                "--disable-sync", "--disable-component-update", "--disable-default-apps",
                "--user-data-dir=" + work.resolve("perfil"),
                "--host-resolver-rules=MAP * ~NOTFOUND",
                "--proxy-server=" + OfficeRunner.DEAD_PROXY,
                "--proxy-bypass-list=<-loopback>",
                "--no-pdf-header-footer", "--print-to-pdf-no-header",
                "--print-to-pdf=" + printed,
                input.toAbsolutePath().toUri().toString());
    }

    private static URI baseOf(Path input) {
        Path parent = input.toAbsolutePath().getParent();
        String uri = (parent == null ? input.toAbsolutePath() : parent).toUri().toString();
        return URI.create(uri.endsWith("/") ? uri : uri + "/");
    }

    private static void requireHtml(Path input) throws PdfLocalException {
        Path name = input.getFileName();
        String text = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
        if (!(text.endsWith(".html") || text.endsWith(".htm"))) {
            throw new PdfLocalException(Messages.CONVERT_UNSUPPORTED_HTML);
        }
        try {
            if (Files.size(input) > MAX_BYTES) {
                throw new PdfLocalException(Messages.CONVERT_UNSUPPORTED_HTML);
            }
            try (InputStream in = Files.newInputStream(input)) {
                byte[] head = in.readNBytes(BINARY_CHECK_BYTES);
                for (byte value : head) {
                    if (value == 0) {
                        throw new PdfLocalException(Messages.CONVERT_UNSUPPORTED_HTML);
                    }
                }
            }
        } catch (IOException e) {
            throw new PdfLocalException(Messages.FILE_UNREADABLE, e);
        }
    }
}
