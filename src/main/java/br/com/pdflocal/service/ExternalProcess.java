package br.com.pdflocal.service;

import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ExternalProcess {

    static final int TIMED_OUT = -1;

    private static final long POLL_MILLIS = 200;

    private ExternalProcess() {
    }

    static int run(List<String> command, Map<String, String> environment, Path workingDirectory, Duration timeout,
                   SaveListener listener) throws IOException, SaveCancelledException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                if (process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return process.exitValue();
                }
                if (listener.isCancelled()) {
                    kill(process);
                    throw new SaveCancelledException();
                }
                if (System.nanoTime() > deadline) {
                    kill(process);
                    return TIMED_OUT;
                }
            }
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new SaveCancelledException();
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
