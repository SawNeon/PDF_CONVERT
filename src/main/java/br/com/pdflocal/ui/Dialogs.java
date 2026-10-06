package br.com.pdflocal.ui;

import br.com.pdflocal.service.ImportResult;
import br.com.pdflocal.util.Messages;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

final class Dialogs {

    private static final int MAX_FAILURES_SHOWN = 8;
    private static final String DEFAULT_OUTPUT_NAME = "documento.pdf";

    private Dialogs() {
    }

    static List<Path> chooseFilesToAdd(Window owner, Path initialDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("dialog.add.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("dialog.add.filter"),
                "*.pdf", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.tif", "*.tiff"));
        applyInitialDirectory(chooser, initialDirectory);
        List<File> files = chooser.showOpenMultipleDialog(owner);
        return files == null ? List.of() : files.stream().map(File::toPath).collect(Collectors.toList());
    }

    static Path chooseOutput(Window owner, Path initialDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("dialog.save.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(Messages.get("dialog.save.filter"), "*.pdf"));
        chooser.setInitialFileName(DEFAULT_OUTPUT_NAME);
        applyInitialDirectory(chooser, initialDirectory);
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return null;
        }
        Path path = file.toPath();
        String name = path.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? path : path.resolveSibling(name + ".pdf");
    }

    static boolean confirmClear(Window owner) {
        ButtonType confirm = new ButtonType(Messages.get("dialog.clear.confirm"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("dialog.clear.message"), confirm, cancel);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog.clear.title"));
        alert.setHeaderText(null);
        return alert.showAndWait().filter(button -> button == confirm).isPresent();
    }

    static boolean confirmSignatureLoss(Window owner) {
        ButtonType proceed = new ButtonType(Messages.get("dialog.signature.continue"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING, Messages.get("dialog.signature.message"), proceed, cancel);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog.signature.title"));
        alert.setHeaderText(null);
        return alert.showAndWait().filter(button -> button == proceed).isPresent();
    }

    static void showError(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog.error.title"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    static void showImportFailures(Window owner, List<ImportResult.Failure> failures) {
        String lines = failures.stream()
                .limit(MAX_FAILURES_SHOWN)
                .map(failure -> failure.fileName() + ": " + failure.message())
                .collect(Collectors.joining("\n"));
        if (failures.size() > MAX_FAILURES_SHOWN) {
            lines += "\n" + Messages.format("dialog.failures.more", failures.size() - MAX_FAILURES_SHOWN);
        }
        Alert alert = new Alert(Alert.AlertType.WARNING, lines);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("dialog.error.title"));
        alert.setHeaderText(Messages.get("dialog.failures.title"));
        alert.showAndWait();
    }

    private static void applyInitialDirectory(FileChooser chooser, Path directory) {
        if (directory != null && Files.isDirectory(directory)) {
            chooser.setInitialDirectory(directory.toFile());
        }
    }
}
