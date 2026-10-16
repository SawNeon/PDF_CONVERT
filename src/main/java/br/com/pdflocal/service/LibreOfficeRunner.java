package br.com.pdflocal.service;

import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LibreOfficeRunner {

    private static final Logger LOG = Logger.getLogger(LibreOfficeRunner.class.getName());

    private LibreOfficeRunner() {
    }

    static Path convert(Path soffice, Path home, Path input, List<String> convertArguments, String extension,
                        Path work, SaveListener listener) throws PdfLocalException, SaveCancelledException, IOException {
        Files.createDirectories(home);
        Path outDirectory = Files.createTempDirectory(home, "o");
        try {
            List<String> arguments = new ArrayList<>(convertArguments);
            arguments.add("--outdir");
            arguments.add(outDirectory.toString());
            arguments.add(input.toAbsolutePath().toString());
            Path profile = LibreOfficeProfile.ensure(home);
            try {
                OfficeRunner.runLibreOffice(soffice, profile, work, arguments, listener);
            } catch (PdfLocalException first) {
                LOG.log(Level.INFO, "Retrying the conversion with a fresh LibreOffice profile");
                OutputFiles.deleteTree(profile);
                OfficeRunner.runLibreOffice(soffice, LibreOfficeProfile.ensure(home), work, arguments, listener);
            }
            Path produced = find(outDirectory, input, extension);
            Path kept = work.resolve("saida." + extension);
            Files.copy(produced, kept, StandardCopyOption.REPLACE_EXISTING);
            return kept;
        } finally {
            OutputFiles.deleteTree(outDirectory);
        }
    }

    private static Path find(Path outDirectory, Path input, String extension) throws IOException, PdfLocalException {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path expected = outDirectory.resolve((dot > 0 ? name.substring(0, dot) : name) + "." + extension);
        if (Files.isRegularFile(expected)) {
            return expected;
        }
        try (var files = Files.list(outDirectory)) {
            return files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension))
                    .findFirst().orElseThrow(() -> new PdfLocalException(Messages.CONVERT_FAILED));
        }
    }
}
