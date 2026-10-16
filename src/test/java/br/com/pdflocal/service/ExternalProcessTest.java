package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.pdflocal.util.SaveCancelledException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalProcessTest {

    private static final Path POWERSHELL = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"),
            "System32", "WindowsPowerShell", "v1.0", "powershell.exe");

    @BeforeEach
    void requireWindowsPowerShell() {
        assumeTrue(Files.isRegularFile(POWERSHELL));
    }

    @Test
    void returnsTheExitCode() throws Exception {
        assertEquals(0, run("exit 0", Duration.ofSeconds(30), SaveListener.NONE));
        assertEquals(7, run("exit 7", Duration.ofSeconds(30), SaveListener.NONE));
    }

    @Test
    void environmentVariablesReachTheProcess() throws Exception {
        List<String> command = List.of(POWERSHELL.toString(), "-NoProfile", "-NonInteractive", "-Command",
                "if ($env:PDFLOCAL_TEST -eq 'ola mundo') { exit 5 } else { exit 6 }");

        int code = ExternalProcess.run(command, Map.of("PDFLOCAL_TEST", "ola mundo"), null, Duration.ofSeconds(30),
                SaveListener.NONE);

        assertEquals(5, code);
    }

    @Test
    void aSlowProcessIsKilledOnTimeout() throws Exception {
        long start = System.nanoTime();

        int code = run("Start-Sleep -Seconds 60", Duration.ofSeconds(2), SaveListener.NONE);

        assertEquals(ExternalProcess.TIMED_OUT, code);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 20);
    }

    @Test
    void cancellingKillsTheProcess() {
        SaveListener cancelled = new SaveListener() {
            @Override
            public void onPage(int done, int total) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        long start = System.nanoTime();

        assertThrows(SaveCancelledException.class, () -> run("Start-Sleep -Seconds 60", Duration.ofSeconds(60), cancelled));

        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 20);
    }

    private static int run(String script, Duration timeout, SaveListener listener) throws Exception {
        return ExternalProcess.run(List.of(POWERSHELL.toString(), "-NoProfile", "-NonInteractive", "-Command", script),
                Map.of(), null, timeout, listener);
    }
}
