package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.service.ImportResult;
import br.com.pdflocal.util.AppSettings;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public final class MainView extends BorderPane implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MainView.class.getName());

    private final Stage stage;
    private final AppSettings settings;
    private final DocumentSession session = new DocumentSession();
    private final PageGrid grid = new PageGrid(session);
    private final Toolbar toolbar;
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private final Button cancelButton = new Button(Messages.get("dialog.cancel"));

    private DocumentSession.SaveOperation saving;
    private boolean cancelRequested;
    private boolean busy;
    private String busyMessage = "";
    private String notice;
    private Path lastDirectory;

    public MainView(Stage stage, AppSettings settings) {
        this.stage = stage;
        this.settings = settings;
        this.toolbar = new Toolbar(this::addFiles, this::clear, this::save, grid::setViewMode,
                this::toggleTheme, Theme.parse(settings.theme()));

        progress.setPrefWidth(160);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        cancelButton.setVisible(false);
        cancelButton.setOnAction(event -> cancelSave());
        HBox statusBar = new HBox(status, spacer, progress, cancelButton);
        statusBar.getStyleClass().add("status-bar");

        setTop(toolbar);
        setCenter(grid);
        setBottom(statusBar);

        session.items().addListener((ListChangeListener<PageItem>) change -> {
            notice = null;
            refreshState();
        });
        grid.setOnPreview(index -> PreviewPane.open(stage, session, session.items(), index));
        grid.selectedCountProperty().addListener((observable, oldValue, newValue) -> refreshState());
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        setOnDragOver(this::onDragOver);
        setOnDragDropped(this::onDragDropped);
        refreshState();
    }

    public void importFiles(List<Path> files) {
        if (busy || files.isEmpty()) {
            return;
        }
        Path parent = files.get(0).toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
        startBusy(Messages.get("status.importing"));
        session.importFiles(files).whenComplete((result, error) -> Platform.runLater(() -> finishImport(result, error)));
    }

    @Override
    public void close() {
        session.close();
    }

    private void toggleTheme() {
        Theme next = Theme.parse(settings.theme()).toggled();
        Application.setUserAgentStylesheet(next.userAgentStylesheet());
        settings.setTheme(next.name());
        toolbar.setTheme(next);
    }

    private void addFiles() {
        importFiles(Dialogs.chooseFilesToAdd(stage, lastDirectory));
    }

    private void clear() {
        if (busy || session.items().isEmpty() || !Dialogs.confirmClear(stage)) {
            return;
        }
        startBusy(Messages.get("status.clearing"));
        session.clear().whenComplete((ignored, error) -> Platform.runLater(() -> {
            endBusy();
            if (error != null) {
                LOG.log(Level.WARNING, "Failed to clear: {0}", error.getClass().getSimpleName());
            }
        }));
    }

    private void save() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        if (session.hasSignedSources() && !Dialogs.confirmSignatureLoss(stage)) {
            return;
        }
        Path output = Dialogs.chooseOutput(stage, lastDirectory);
        if (output == null) {
            return;
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
        startBusy(Messages.get("status.saving"));
        grid.setEditable(false);
        progress.setProgress(0);
        cancelRequested = false;
        AtomicInteger lastPercent = new AtomicInteger(-1);
        saving = session.startSave(output, toolbar.pageSize(), (done, total) -> {
            int percent = done * 100 / total;
            if (lastPercent.getAndSet(percent) != percent) {
                Platform.runLater(() -> showSaveProgress(done, total));
            }
        });
        refreshState();
        saving.result().whenComplete((pages, error) -> Platform.runLater(() -> finishSave(pages, error)));
    }

    private void cancelSave() {
        if (saving == null || cancelRequested) {
            return;
        }
        cancelRequested = true;
        saving.cancel().run();
        busyMessage = Messages.get("status.cancelling");
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        refreshState();
    }

    private void showSaveProgress(int done, int total) {
        if (saving == null || cancelRequested) {
            return;
        }
        if (done >= total) {
            progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            busyMessage = Messages.get("status.saving.finishing");
        } else {
            progress.setProgress(done / (double) total);
            busyMessage = Messages.format("status.saving.progress", done, total);
        }
        refreshState();
    }

    private void finishSave(Integer pages, Throwable error) {
        saving = null;
        grid.setEditable(true);
        endBusy();
        if (error == null) {
            notice = Messages.format("status.saved", pages);
        } else if (causeOf(error) instanceof SaveCancelledException) {
            notice = Messages.get(Messages.SAVE_CANCELLED);
        } else {
            Dialogs.showError(stage, messageOf(error, Messages.SAVE_FAILED));
        }
        refreshState();
    }

    private void finishImport(ImportResult result, Throwable error) {
        endBusy();
        if (error != null) {
            LOG.log(Level.WARNING, "Import failed: {0}", error.getClass().getSimpleName());
            Dialogs.showError(stage, Messages.get(Messages.UNEXPECTED));
            return;
        }
        session.append(result.items());
        if (!result.failures().isEmpty()) {
            Dialogs.showImportFailures(stage, result.failures());
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if (busy) {
            return;
        }
        if (event.getCode() == KeyCode.DELETE) {
            grid.deleteSelection();
            event.consume();
        } else if (event.isShortcutDown() && !event.isShiftDown() && event.getCode() == KeyCode.Z) {
            session.undo();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.A) {
            grid.selectAll();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            grid.clearSelection();
            event.consume();
        }
    }

    private void onDragOver(DragEvent event) {
        if (!busy && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        }
    }

    private void onDragDropped(DragEvent event) {
        boolean accepted = false;
        if (!busy && event.getDragboard().hasFiles()) {
            List<Path> files = event.getDragboard().getFiles().stream().map(File::toPath).toList();
            importFiles(files);
            accepted = true;
        }
        event.setDropCompleted(accepted);
        event.consume();
    }

    private void startBusy(String message) {
        busy = true;
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        busyMessage = message;
        notice = null;
        refreshState();
    }

    private void endBusy() {
        busy = false;
        refreshState();
    }

    private void refreshState() {
        boolean hasPages = !session.items().isEmpty();
        toolbar.setState(busy, hasPages);
        progress.setVisible(busy);
        cancelButton.setVisible(busy && saving != null);
        cancelButton.setDisable(cancelRequested);
        if (busy) {
            status.setText(busyMessage);
        } else if (notice != null) {
            status.setText(notice);
        } else if (hasPages) {
            String summary = Messages.format("status.summary", session.items().size(), session.fileCount());
            int selected = grid.selectedCountProperty().get();
            status.setText(selected > 0 ? summary + "  |  " + Messages.format("status.selection", selected) : summary);
        } else {
            status.setText(Messages.get("status.empty"));
        }
    }

    private static Throwable causeOf(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static String messageOf(Throwable error, String fallbackKey) {
        Throwable cause = causeOf(error);
        return cause instanceof PdfLocalException ? cause.getMessage() : Messages.get(fallbackKey);
    }
}
