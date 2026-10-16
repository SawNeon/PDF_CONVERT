package br.com.pdflocal.service;

import br.com.pdflocal.model.OfficeKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class WindowsSystemProbe implements SystemProbe {

    private static final List<String> OFFICE_FOLDERS = List.of(
            "Microsoft Office\\root\\Office16", "Microsoft Office\\root\\Office15", "Microsoft Office\\Office16",
            "Microsoft Office\\Office15", "Microsoft Office\\Office14");

    @Override
    public boolean officeInstalled(OfficeKind app) {
        for (Path base : programFolders()) {
            for (String folder : OFFICE_FOLDERS) {
                if (Files.isRegularFile(base.resolve(folder).resolve(app.executable()))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean officeRunning(OfficeKind app) {
        String executable = app.executable().toLowerCase(Locale.ROOT);
        return ProcessHandle.allProcesses().anyMatch(process -> process.info().command()
                .map(command -> command.toLowerCase(Locale.ROOT).endsWith("\\" + executable)).orElse(false));
    }

    @Override
    public Optional<Path> libreOffice() {
        return firstExisting("LibreOffice\\program\\soffice.exe");
    }

    @Override
    public Optional<Path> browser() {
        Optional<Path> edge = firstExisting("Microsoft\\Edge\\Application\\msedge.exe");
        return edge.isPresent() ? edge : firstExisting("Google\\Chrome\\Application\\chrome.exe");
    }

    private static Optional<Path> firstExisting(String relative) {
        for (Path base : programFolders()) {
            Path candidate = base.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static List<Path> programFolders() {
        List<Path> folders = new ArrayList<>();
        for (String variable : new String[] {"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                folders.add(Path.of(value));
            }
        }
        return folders;
    }
}
