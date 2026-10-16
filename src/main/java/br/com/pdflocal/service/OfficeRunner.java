package br.com.pdflocal.service;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class OfficeRunner {

    static final Duration TIMEOUT = Duration.ofMinutes(5);

    static final String DEAD_PROXY = "http://127.0.0.1:9";
    private static final String SCRIPT_RESOURCE = "/scripts/office-convert.ps1";
    private static final String SCRIPT = loadScript();
    private static final Logger LOG = Logger.getLogger(OfficeRunner.class.getName());

    private OfficeRunner() {
    }

    static void runScript(OfficeKind kind, String action, Path input, Path output, Path list,
                          SaveListener listener) throws PdfLocalException, SaveCancelledException {
        long since = System.currentTimeMillis();
        Map<String, String> environment = new HashMap<>();
        environment.put("PDFLOCAL_ACTION", action);
        if (input != null) {
            environment.put("PDFLOCAL_IN", input.toAbsolutePath().toString());
        }
        if (output != null) {
            environment.put("PDFLOCAL_OUT", output.toAbsolutePath().toString());
        }
        if (list != null) {
            environment.put("PDFLOCAL_LIST", list.toAbsolutePath().toString());
        }
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        List<String> command = List.of(powershell().toString(), "-NoProfile", "-NonInteractive", "-EncodedCommand",
                encoded);
        try {
            finish(run(command, environment, null, listener), action);
        } catch (PdfLocalException | RuntimeException e) {
            closeHiddenInstances(kind, since);
            throw e;
        }
    }

    static void closeHiddenInstances(OfficeKind kind, long sinceMillis) {
        String name = kind.executable().replaceFirst("(?i)\\.exe$", "");
        String script = "Get-Process -Name $env:PDFLOCAL_PROCESS -ErrorAction SilentlyContinue | "
                + "Where-Object { $_.MainWindowHandle -eq 0 -and "
                + "$_.StartTime -gt [DateTimeOffset]::FromUnixTimeMilliseconds([long]$env:PDFLOCAL_SINCE).LocalDateTime } | "
                + "Stop-Process -Force";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            ExternalProcess.run(List.of(powershell().toString(), "-NoProfile", "-NonInteractive", "-EncodedCommand",
                    encoded), Map.of("PDFLOCAL_PROCESS", name, "PDFLOCAL_SINCE", String.valueOf(sinceMillis)), null,
                    Duration.ofSeconds(30), SaveListener.NONE);
        } catch (IOException | SaveCancelledException e) {
            LOG.log(Level.WARNING, "Could not close the hidden Office instance: {0}", e.getClass().getSimpleName());
        }
    }

    static void runLibreOffice(Path soffice, Path profile, Path workDirectory, List<String> arguments,
                               SaveListener listener) throws PdfLocalException, SaveCancelledException {
        List<String> command = new ArrayList<>(List.of(soffice.toString(), "--headless", "--norestore",
                "--nolockcheck", "--nodefault", "--nologo", "-env:UserInstallation=" + LibreOfficeProfile.uri(profile)));
        command.addAll(arguments);
        finish(run(command, libreOfficeEnvironment(), workDirectory, listener), "libreoffice");
    }

    static Map<String, String> libreOfficeEnvironment() {
        Map<String, String> environment = new HashMap<>();
        for (String name : List.of("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")) {
            environment.put(name, DEAD_PROXY);
        }
        environment.put("NO_PROXY", "");
        return environment;
    }

    private static int run(List<String> command, Map<String, String> environment, Path directory,
                           SaveListener listener) throws PdfLocalException, SaveCancelledException {
        try {
            return ExternalProcess.run(command, environment, directory, TIMEOUT, listener);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to start the converter: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(Messages.CONVERT_FAILED, e);
        }
    }

    private static void finish(int exitCode, String what) throws PdfLocalException {
        if (exitCode == ExternalProcess.TIMED_OUT) {
            LOG.log(Level.WARNING, "Converter timed out: {0}", what);
            throw new PdfLocalException(Messages.CONVERT_TIMEOUT);
        }
        if (exitCode != 0) {
            LOG.log(Level.WARNING, "Converter failed: {0}, code {1}", new Object[] {what, exitCode});
            throw new PdfLocalException(Messages.CONVERT_FAILED);
        }
    }

    private static Path powershell() {
        String root = System.getenv("SystemRoot");
        return Path.of(root == null ? "C:\\Windows" : root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
    }

    private static String loadScript() {
        try (InputStream in = OfficeRunner.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing script resource");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean looksLikePdf(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(5);
            return head.length == 5 && new String(head, StandardCharsets.US_ASCII).equals("%PDF-");
        } catch (IOException e) {
            return false;
        }
    }
}
