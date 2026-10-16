package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.testsupport.OfficeFixtures;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class OfficeConverterTest {

    private static final SystemProbe REAL = new WindowsSystemProbe();

    @TempDir
    Path directory;

    private Path libreOfficeHome;

    @BeforeEach
    void createShortLibreOfficeHome() throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(local, "Temp");
        libreOfficeHome = Files.createTempDirectory(base, "pl");
    }

    @AfterEach
    void removeLibreOfficeHome() {
        OutputFiles.deleteTree(libreOfficeHome);
    }

    private OfficeConverter converter(SystemProbe probe) {
        return new OfficeConverter(probe, libreOfficeHome);
    }

    private record FakeProbe(boolean installed, boolean running, boolean hasLibreOffice) implements SystemProbe {

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
            return hasLibreOffice ? Optional.of(Path.of("soffice.exe")) : Optional.empty();
        }

        @Override
        public Optional<Path> browser() {
            return Optional.empty();
        }
    }

    @Test
    void anInstalledAndClosedOfficeProgramIsPreferred() throws Exception {
        assertEquals(OfficeConverter.Engine.MICROSOFT_OFFICE,
                converter(new FakeProbe(true, false, true)).engineFor(OfficeKind.WORD));
    }

    @Test
    void anOfficeProgramThatIsAlreadyOpenIsNeverAutomated() throws Exception {
        assertEquals(OfficeConverter.Engine.LIBREOFFICE,
                converter(new FakeProbe(true, true, true)).engineFor(OfficeKind.EXCEL));

        PdfLocalException error = assertThrows(PdfLocalException.class,
                () -> converter(new FakeProbe(true, true, false)).engineFor(OfficeKind.EXCEL));
        assertEquals(Messages.get(Messages.CONVERT_APP_RUNNING), error.getMessage());
    }

    @Test
    void withoutOfficeTheLibreOfficeIsUsedOrTheUserIsTold() throws Exception {
        assertEquals(OfficeConverter.Engine.LIBREOFFICE,
                converter(new FakeProbe(false, false, true)).engineFor(OfficeKind.POWERPOINT));

        PdfLocalException error = assertThrows(PdfLocalException.class,
                () -> converter(new FakeProbe(false, false, false)).engineFor(OfficeKind.WORD));
        assertEquals(Messages.get(Messages.CONVERT_NO_ENGINE), error.getMessage());
    }

    @Test
    void invalidFilesAreRejectedBeforeAnyProgramIsStarted() throws Exception {
        OfficeConverter converter = converter(new FakeProbe(false, false, false));
        Path fake = directory.resolve("fake.docx");
        Files.writeString(fake, "nao sou um docx");

        PdfLocalException error = assertThrows(PdfLocalException.class,
                () -> converter.convert(fake, directory.resolve("out.pdf"), SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_UNSUPPORTED), error.getMessage());
    }

    @Test
    void theOutputCannotBeTheInputAndTheFolderMustExist() throws Exception {
        OfficeConverter converter = converter(new FakeProbe(false, false, true));
        Path docx = OfficeFixtures.docx(directory, "a.docx", "texto");

        assertThrows(PdfLocalException.class, () -> converter.convert(docx, docx, SaveListener.NONE));
        assertThrows(PdfLocalException.class,
                () -> converter.convert(docx, directory.resolve("missing").resolve("out.pdf"), SaveListener.NONE));
    }

    @Test
    void libreOfficeUpdateTrafficIsDivertedToADeadLocalProxy() {
        Map<String, String> environment = OfficeRunner.libreOfficeEnvironment();

        for (String name : List.of("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY")) {
            assertEquals("http://127.0.0.1:9", environment.get(name), name);
        }
        assertEquals("", environment.get("NO_PROXY"));
    }

    @Test
    void theLibreOfficeProfileSeedDisablesMacrosLinksAndTheRecentList() throws Exception {
        Path profile = LibreOfficeProfile.ensure(libreOfficeHome);

        String seed = Files.readString(profile.resolve("user").resolve("registrymodifications.xcu"));
        assertTrue(seed.contains("MacroSecurityLevel"));
        assertTrue(seed.contains("<prop oor:name=\"PickListSize\" oor:op=\"fuse\"><value>0</value>"));
        assertTrue(seed.contains("http://127.0.0.1:9/"));
    }

    @Test
    @Tag("office")
    void libreOfficeConvertsAWordDocument() throws Exception {
        assumeTrue(REAL.libreOffice().isPresent());
        Path docx = OfficeFixtures.docx(directory, "laudo.docx", "LAUDO-ABC123");
        Path output = directory.resolve("laudo.pdf");

        OfficeConverter.Engine engine = converter(libreOfficeOnly()).convert(docx, output, SaveListener.NONE);

        assertEquals(OfficeConverter.Engine.LIBREOFFICE, engine);
        assertTrue(TestFiles.pageTexts(output).get(0).contains("LAUDO-ABC123"));
        assertNoLeftovers();
    }

    @Test
    @Tag("office")
    void libreOfficeConvertsASpreadsheet() throws Exception {
        assumeTrue(REAL.libreOffice().isPresent());
        Path xlsx = OfficeFixtures.xlsx(directory, "planilha.xlsx", "PLANILHA-XYZ");
        Path output = directory.resolve("planilha.pdf");

        converter(libreOfficeOnly()).convert(xlsx, output, SaveListener.NONE);

        assertTrue(TestFiles.pageTexts(output).get(0).contains("PLANILHA-XYZ"));
        assertNoLeftovers();
    }

    @Test
    @Tag("office")
    void wordConvertsADocumentWhenItIsInstalledAndClosed() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.WORD) && !REAL.officeRunning(OfficeKind.WORD));
        Set<Long> before = pidsOf(OfficeKind.WORD);
        Path docx = OfficeFixtures.docx(directory, "oficio.docx", "OFICIO-QWE789");
        Path output = directory.resolve("oficio.pdf");

        OfficeConverter.Engine engine = converter(REAL).convert(docx, output, SaveListener.NONE);

        assertEquals(OfficeConverter.Engine.MICROSOFT_OFFICE, engine);
        assertTrue(TestFiles.pageTexts(output).get(0).contains("OFICIO-QWE789"));
        assertNoLeftovers();
        assertNoNewProcess(OfficeKind.WORD, before);
    }

    @Test
    @Tag("office")
    void excelConvertsASpreadsheetWhenItIsInstalledAndClosed() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.EXCEL) && !REAL.officeRunning(OfficeKind.EXCEL));
        Set<Long> before = pidsOf(OfficeKind.EXCEL);
        Path xlsx = OfficeFixtures.xlsx(directory, "dados.xlsx", "DADOS-RTY456");
        Path output = directory.resolve("dados.pdf");

        OfficeConverter.Engine engine = converter(REAL).convert(xlsx, output, SaveListener.NONE);

        assertEquals(OfficeConverter.Engine.MICROSOFT_OFFICE, engine);
        assertTrue(TestFiles.pageTexts(output).get(0).contains("DADOS-RTY456"));
        assertNoNewProcess(OfficeKind.EXCEL, before);
    }

    @Test
    @Tag("office")
    void powerPointConvertsAPresentationWhenItIsInstalledAndClosed() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.POWERPOINT) && !REAL.officeRunning(OfficeKind.POWERPOINT));
        Set<Long> before = pidsOf(OfficeKind.POWERPOINT);
        Path pptx = directory.resolve("apresentacao.pptx");
        createPresentation(pptx, "SLIDE-UIO321");
        assertNoNewProcess(OfficeKind.POWERPOINT, before);
        Path output = directory.resolve("apresentacao.pdf");

        OfficeConverter.Engine engine = converter(REAL).convert(pptx, output, SaveListener.NONE);

        assertEquals(OfficeConverter.Engine.MICROSOFT_OFFICE, engine);
        assertTrue(TestFiles.pageTexts(output).get(0).contains("SLIDE-UIO321"));
        assertNoNewProcess(OfficeKind.POWERPOINT, before);
    }

    @Test
    @Tag("office")
    void hiddenOfficeInstancesLeftByAFailedRunAreClosedButWindowedOnesAreNot() throws Exception {
        assumeTrue(REAL.officeInstalled(OfficeKind.WORD) && !REAL.officeRunning(OfficeKind.WORD));
        long since = System.currentTimeMillis();
        String script = "$app = New-Object -ComObject Word.Application; $app.Visible = $false; Start-Sleep -Seconds 60";
        String powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0",
                "powershell.exe").toString();
        Process starter = new ProcessBuilder(powershell, "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (!REAL.officeRunning(OfficeKind.WORD) && System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
            }
            assertTrue(REAL.officeRunning(OfficeKind.WORD), "the hidden Word did not start");

            OfficeRunner.closeHiddenInstances(OfficeKind.WORD, since);

            long limit = System.currentTimeMillis() + 20_000;
            while (REAL.officeRunning(OfficeKind.WORD) && System.currentTimeMillis() < limit) {
                Thread.sleep(500);
            }
            assertTrue(!REAL.officeRunning(OfficeKind.WORD), "the hidden Word was left running");
        } finally {
            starter.descendants().forEach(ProcessHandle::destroyForcibly);
            starter.destroyForcibly();
        }
    }

    @Test
    void aMacroDocumentIsRefusedBeforeConversion() throws Exception {
        Path macro = directory.resolve("macro.docx");
        try (OutputStream out = Files.newOutputStream(macro); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String part : List.of("word/document.xml", "word/vbaProject.bin")) {
                zip.putNextEntry(new ZipEntry(part));
                zip.write("<x/>".getBytes(StandardCharsets.US_ASCII));
                zip.closeEntry();
            }
        }

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> converter(REAL)
                .convert(macro, directory.resolve("out.pdf"), SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_MACROS), error.getMessage());
    }

    private SystemProbe libreOfficeOnly() {
        return new SystemProbe() {
            @Override
            public boolean officeInstalled(OfficeKind app) {
                return false;
            }

            @Override
            public boolean officeRunning(OfficeKind app) {
                return false;
            }

            @Override
            public Optional<Path> libreOffice() {
                return REAL.libreOffice();
            }

            @Override
            public Optional<Path> browser() {
                return Optional.empty();
            }
        };
    }

    private static Set<Long> pidsOf(OfficeKind kind) {
        String executable = kind.executable().toLowerCase(Locale.ROOT);
        return ProcessHandle.allProcesses()
                .filter(process -> process.info().command()
                        .map(command -> command.toLowerCase(Locale.ROOT).endsWith("\\" + executable)).orElse(false))
                .map(ProcessHandle::pid).collect(Collectors.toSet());
    }

    private static void assertNoNewProcess(OfficeKind kind, Set<Long> before) throws InterruptedException {
        Set<Long> leftover = Set.of();
        for (int attempt = 0; attempt < 30; attempt++) {
            leftover = pidsOf(kind).stream().filter(pid -> !before.contains(pid)).collect(Collectors.toSet());
            if (leftover.isEmpty()) {
                return;
            }
            Thread.sleep(500);
        }
        assertTrue(leftover.isEmpty(), kind + " was left running: " + leftover);
    }

    private void assertNoLeftovers() throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("pdflocal-")),
                    "temporary work folder was not removed");
        }
    }

    private static void createPresentation(Path target, String text) throws Exception {
        String script = "$ErrorActionPreference='Stop';"
                + "$app = New-Object -ComObject PowerPoint.Application;"
                + "$p = $app.Presentations.Add(0);"
                + "$s = $p.Slides.Add(1, 12);"
                + "$t = $s.Shapes.AddTextbox(1, 50, 50, 500, 60);"
                + "$t.TextFrame.TextRange.Text = $env:PDFLOCAL_TEXT;"
                + "$p.SaveAs($env:PDFLOCAL_OUT, 24);"
                + "$p.Close();"
                + "if ($app.Presentations.Count -eq 0) { $app.Quit() }";
        String powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0",
                "powershell.exe").toString();
        int code = ExternalProcess.run(List.of(powershell, "-NoProfile", "-NonInteractive", "-Command", script),
                Map.of("PDFLOCAL_TEXT", text, "PDFLOCAL_OUT", target.toString()), null, Duration.ofMinutes(2),
                SaveListener.NONE);
        assumeTrue(code == 0 && Files.isRegularFile(target), "could not create the sample presentation");
    }
}
