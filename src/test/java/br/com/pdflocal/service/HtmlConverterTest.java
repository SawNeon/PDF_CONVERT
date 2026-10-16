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
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class HtmlConverterTest {

    private static final SystemProbe REAL = new WindowsSystemProbe();

    @TempDir
    Path directory;

    private Path home;

    @BeforeEach
    void createShortHome() throws Exception {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(local, "Temp");
        home = Files.createTempDirectory(base, "ph");
    }

    @AfterEach
    void removeHome() {
        OutputFiles.deleteTree(home);
    }

    @Test
    void theBrowserIsStartedWithoutNetworkScriptsOrSharedProfile() {
        List<String> command = HtmlConverter.command(Path.of("C:/edge/msedge.exe"), Path.of("C:/work"),
                Path.of("C:/work/saida.pdf"), Path.of("C:/docs/a.html"));

        assertTrue(command.contains("--proxy-server=http://127.0.0.1:9"));
        assertTrue(command.contains("--proxy-bypass-list=<-loopback>"));
        assertTrue(command.contains("--host-resolver-rules=MAP * ~NOTFOUND"));
        assertTrue(command.stream().anyMatch(argument -> argument.startsWith("--user-data-dir=")));
        assertTrue(command.get(command.size() - 1).startsWith("file:///"));
    }

    @Test
    void thePolicyBlocksScriptsAndKeepsRelativeResourcesPointingAtTheOriginalFolder() {
        byte[] original = "<!DOCTYPE html><html><head><title>t</title></head><body onload=\"x()\">ol\u00e1</body></html>"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        String result = new String(HtmlPolicy.apply(original, java.net.URI.create("file:///C:/docs/")),
                java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(result.contains("<head><meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'none'"));
        assertTrue(result.contains("<base href=\"file:///C:/docs/\">"));
        assertTrue(result.contains("ol\u00e1"));
    }

    @Test
    void thePolicyIsInsertedEvenWithoutAHeadElement() {
        for (String page : new String[] {"<html><body>x</body></html>", "<!DOCTYPE html><p>x</p>", "<p>x</p>"}) {
            String result = new String(HtmlPolicy.apply(page.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    java.net.URI.create("file:///C:/d/")), java.nio.charset.StandardCharsets.US_ASCII);

            assertTrue(result.contains("Content-Security-Policy"), page);
            assertTrue(result.indexOf("Content-Security-Policy") < result.indexOf("x</"), page);
        }
    }

    @Test
    void bytesOutsideAsciiSurviveUnchanged() {
        byte[] original = new byte[] {'<', 'h', 'e', 'a', 'd', '>', '<', '/', 'h', 'e', 'a', 'd', '>', (byte) 0xE9,
            (byte) 0xC3, (byte) 0xA3};

        byte[] result = HtmlPolicy.apply(original, java.net.URI.create("file:///C:/d/"));

        assertEquals((byte) 0xE9, result[result.length - 3]);
        assertEquals((byte) 0xC3, result[result.length - 2]);
        assertEquals((byte) 0xA3, result[result.length - 1]);
    }

    @Test
    void onlyHtmlTextFilesAreAccepted() throws Exception {
        HtmlConverter converter = new HtmlConverter(fakeBrowser(), home);
        Path text = directory.resolve("a.txt");
        Files.writeString(text, "<html></html>");
        Path binary = directory.resolve("b.html");
        Files.write(binary, new byte[] {'<', 'h', 0, 1, 2});

        for (Path file : List.of(text, binary)) {
            PdfLocalException error = assertThrows(PdfLocalException.class,
                    () -> converter.convert(file, directory.resolve("out.pdf"), SaveListener.NONE));
            assertEquals(Messages.get(Messages.CONVERT_UNSUPPORTED_HTML), error.getMessage());
        }
    }

    @Test
    void withoutABrowserTheUserIsTold() throws Exception {
        Path html = OfficeFixtures.html(directory, "a.html", "<p>oi</p>");
        HtmlConverter converter = new HtmlConverter(new SystemProbe() {
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
                return Optional.empty();
            }

            @Override
            public Optional<Path> browser() {
                return Optional.empty();
            }
        }, home);

        PdfLocalException error = assertThrows(PdfLocalException.class,
                () -> converter.convert(html, directory.resolve("out.pdf"), SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_NO_ENGINE), error.getMessage());
    }

    @Test
    @Tag("office")
    void aPageIsPrintedToPdfWithoutRunningScripts() throws Exception {
        assumeTrue(REAL.browser().isPresent());
        Path html = OfficeFixtures.html(directory, "pagina.html",
                "<h1>TITULO-HTML</h1><script>document.write('JS-EXECUTOU')</script>");
        Path output = directory.resolve("pagina.pdf");

        new HtmlConverter(REAL, home).convert(html, output, SaveListener.NONE);

        String text = TestFiles.pageTexts(output).get(0);
        assertTrue(text.contains("TITULO-HTML"), text);
        assertTrue(!text.contains("JS-EXECUTOU"), "scripts must not run");
        try (Stream<Path> files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("pdflocal-")));
        }
    }

    @Test
    @Tag("office")
    void remoteResourcesAreNeverRequested() throws Exception {
        assumeTrue(REAL.browser().isPresent());
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket ignored = server.accept()) {
                        connections.incrementAndGet();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            int port = server.getLocalPort();
            Path html = OfficeFixtures.html(directory, "remoto.html",
                    "<p>TEXTO-LOCAL</p><img src=\"http://127.0.0.1:" + port + "/pixel.png\">"
                            + "<link rel=\"stylesheet\" href=\"http://localhost:" + port + "/estilo.css\">");
            Path output = directory.resolve("remoto.pdf");

            new HtmlConverter(REAL, home).convert(html, output, SaveListener.NONE);

            assertTrue(TestFiles.pageTexts(output).get(0).contains("TEXTO-LOCAL"));
            assertEquals(0, connections.get(), "the browser contacted a server");
        }
    }

    private SystemProbe fakeBrowser() {
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
                return Optional.empty();
            }

            @Override
            public Optional<Path> browser() {
                return Optional.of(Path.of("browser.exe"));
            }
        };
    }
}
