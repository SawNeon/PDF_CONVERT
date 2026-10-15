package br.com.pdflocal.ui;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.model.FileType;
import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.service.CompressStep;
import br.com.pdflocal.service.CropStep;
import br.com.pdflocal.service.FileTypeDetector;
import br.com.pdflocal.service.FormFiller;
import br.com.pdflocal.service.ImportResult;
import br.com.pdflocal.service.OutputStep;
import br.com.pdflocal.service.PageNumbersStep;
import br.com.pdflocal.service.SaveResult;
import br.com.pdflocal.service.PdfAStep;
import br.com.pdflocal.service.PdfRepairer;
import br.com.pdflocal.service.ProtectStep;
import br.com.pdflocal.service.RedactStep;
import br.com.pdflocal.service.WatermarkStep;
import br.com.pdflocal.util.AppSettings;
import br.com.pdflocal.util.FileSizes;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.OutputNames;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import br.com.pdflocal.util.WrongPasswordException;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
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
    private final Label developerCredit = new Label(Messages.get("app.developer"));

    private DocumentSession.SaveOperation saving;
    private final Deque<ImportResult.Failure> passwordQueue = new ArrayDeque<>();
    private SaveJob savingJob = SaveJob.PLAIN;
    private boolean cancelRequested;
    private boolean busy;
    private String busyMessage = "";
    private String notice;
    private Path lastDirectory;

    public MainView(Stage stage, AppSettings settings) {
        this.stage = stage;
        this.settings = settings;
        Theme theme = Theme.parse(settings.theme());
        Appearance.apply(this, theme);
        this.toolbar = new Toolbar(this::addFiles, this::clear, this::save, tools(), grid::setViewMode,
                this::toggleTheme, theme, this::showAbout);

        progress.setPrefWidth(160);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        cancelButton.setVisible(false);
        cancelButton.setOnAction(event -> cancelSave());
        developerCredit.getStyleClass().add("developer-credit");
        developerCredit.setTooltip(new Tooltip(Messages.get("toolbar.about.tooltip")));
        developerCredit.setOnMouseClicked(event -> showAbout());
        HBox statusBar = new HBox(status, spacer, progress, cancelButton, developerCredit);
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

    private record SaveJob(String suffixKey, List<OutputStep> steps, boolean compressing,
                           ImageExportOptions export) {

        static final SaveJob PLAIN = new SaveJob(null, List.of(), false, null);

        static SaveJob compress(CompressionLevel level) {
            return new SaveJob("output.suffix.compressed", List.of(new CompressStep(level)), true, null);
        }

        static SaveJob of(String suffixKey, OutputStep step) {
            return new SaveJob(suffixKey, List.of(step), false, null);
        }

        static SaveJob exportImages(ImageExportOptions options) {
            return new SaveJob("output.suffix.images", List.of(), false, options);
        }

        String stepKey() {
            return compressing ? "status.compressing" : export != null ? "status.exporting" : "status.processing";
        }

        String afterPagesKey() {
            if (compressing) {
                return "status.compressing.start";
            }
            if (export != null) {
                return "status.exporting.start";
            }
            return steps.isEmpty() ? "status.saving.finishing" : "status.processing.start";
        }
    }

    private List<Tool> tools() {
        return List.of(
                new Tool(ToolGroup.CONVERT_TO_PDF, "tool.images-to-pdf", this::addFiles, false),
                new Tool(ToolGroup.OPTIMIZE, "toolbar.compress", this::compress, true),
                new Tool(ToolGroup.OPTIMIZE, "tool.repair", this::repair, false),
                new Tool(ToolGroup.EDIT, "tool.rotate.right", () -> rotateAll(90), true),
                new Tool(ToolGroup.EDIT, "tool.rotate.left", () -> rotateAll(-90), true),
                new Tool(ToolGroup.EDIT, "tool.numbers", this::pageNumbers, true),
                new Tool(ToolGroup.EDIT, "tool.watermark", this::watermark, true),
                new Tool(ToolGroup.EDIT, "tool.crop", this::crop, true),
                new Tool(ToolGroup.EDIT, "tool.form", this::fillForm, false),
                new Tool(ToolGroup.CONVERT_FROM_PDF, "tool.export-images", this::exportImages, true),
                new Tool(ToolGroup.CONVERT_FROM_PDF, "tool.pdfa", this::convertToPdfA, true),
                new Tool(ToolGroup.SECURITY, "tool.redact", this::redact, true),
                new Tool(ToolGroup.SECURITY, "tool.protect", this::protect, true));
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

    private void showAbout() {
        Dialogs.showAbout(stage);
    }

    private void toggleTheme() {
        Theme next = Theme.parse(settings.theme()).toggled();
        Application.setUserAgentStylesheet(next.userAgentStylesheet());
        Appearance.apply(this, next);
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
        startSaving(SaveJob.PLAIN);
    }

    private void compress() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        int pages = session.items().size();
        int files = session.fileCount();
        long bytes = session.sourceBytes();
        startBusy(Messages.get("status.analyzing"));
        session.imageShare().whenComplete((share, error) -> Platform.runLater(() -> {
            endBusy();
            if (error != null) {
                LOG.log(Level.WARNING, "Analysis failed: {0}", causeOf(error).getClass().getSimpleName());
            }
            CompressDialog.show(stage, pages, files, bytes, error == null ? share : null).ifPresent(level -> startSaving(SaveJob.compress(level)));
        }));
    }

    private void rotateAll(int degrees) {
        if (busy || session.items().isEmpty()) {
            return;
        }
        session.rotate(java.util.stream.IntStream.range(0, session.items().size()).boxed().toList(), degrees);
    }

    private void pageNumbers() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        PageNumbersDialog.show(stage).ifPresent(options ->
                startSaving(SaveJob.of("output.suffix.numbered", new PageNumbersStep(options))));
    }

    private void watermark() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        WatermarkDialog.show(stage, lastDirectory).ifPresent(options -> {
            if (!options.isText() && !isSupportedWatermarkImage(options.imagePath())) {
                Dialogs.showError(stage, Messages.get(Messages.FILE_UNSUPPORTED));
                return;
            }
            startSaving(SaveJob.of("output.suffix.watermarked", new WatermarkStep(options)));
        });
    }

    private void fillForm() {
        if (busy) {
            return;
        }
        Path input = Dialogs.choosePdf(stage, lastDirectory, "dialog.form.choose");
        if (input == null) {
            return;
        }
        Path parent = input.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
        inspectForm(input, null);
    }

    private void inspectForm(Path input, char[] password) {
        startBusy(Messages.get("status.analyzing"));
        session.inspectForm(input, copyOf(password)).whenComplete((info, error) -> Platform.runLater(() -> {
            endBusy();
            if (error != null) {
                failForm(input, password, error, retry -> inspectForm(input, retry));
            } else {
                chooseFormValues(input, password, info);
            }
        }));
    }

    private void chooseFormValues(Path input, char[] password, FormFiller.FormInfo info) {
        Path fileName = input.getFileName();
        String name = fileName == null ? "" : fileName.toString();
        if (info.dynamicXfa()) {
            wipe(password);
            Dialogs.showError(stage, Messages.get(Messages.FORM_XFA));
            return;
        }
        if (info.fields().isEmpty()) {
            wipe(password);
            Dialogs.showError(stage, Messages.get(Messages.FORM_MISSING));
            return;
        }
        if (info.hasSignatures() && !Dialogs.confirmSignatureLoss(stage)) {
            wipe(password);
            return;
        }
        Optional<FormDialog.Answers> answers = FormDialog.show(stage, name, info);
        if (answers.isEmpty()) {
            wipe(password);
            return;
        }
        Path output = Dialogs.chooseOutput(stage, lastDirectory,
                OutputNames.suggest(name, Messages.get("output.suffix.filled")));
        if (output == null) {
            wipe(password);
            return;
        }
        startBusy(Messages.get("status.filling"));
        session.fillForm(input, output, answers.get().changes(), answers.get().flatten(), copyOf(password))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    endBusy();
                    if (error == null) {
                        wipe(password);
                        notice = Messages.format("status.filled", result.filled());
                        refreshState();
                    } else {
                        failForm(input, password, error, retry -> chooseFormValues(input, retry, info));
                    }
                }));
    }

    private void failForm(Path input, char[] password, Throwable error,
                          java.util.function.Consumer<char[]> retryWithPassword) {
        Throwable cause = causeOf(error);
        wipe(password);
        if (cause instanceof PasswordProtectedException) {
            Path name = input.getFileName();
            PasswordDialog.show(stage, name == null ? "" : name.toString(), cause instanceof WrongPasswordException)
                    .ifPresent(retryWithPassword);
            return;
        }
        Dialogs.showError(stage, messageOf(error, Messages.FILE_UNREADABLE));
    }

    private static char[] copyOf(char[] password) {
        return password == null ? null : password.clone();
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private void redact() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        RedactDialog.show(stage, session).ifPresent(areas ->
                startSaving(SaveJob.of("output.suffix.redacted", new RedactStep(areas))));
    }

    private void convertToPdfA() {
        if (busy || session.items().isEmpty() || !Dialogs.confirmPdfA(stage)) {
            return;
        }
        startSaving(SaveJob.of("output.suffix.pdfa", new PdfAStep()));
    }

    private void repair() {
        if (busy) {
            return;
        }
        Path input = Dialogs.choosePdf(stage, lastDirectory, "dialog.repair.choose");
        if (input == null) {
            return;
        }
        Path parent = input.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
        Path name = input.getFileName();
        String suggested = OutputNames.suggest(name == null ? "" : name.toString(), Messages.get("output.suffix.repaired"));
        Path output = Dialogs.chooseOutput(stage, lastDirectory, suggested);
        if (output != null) {
            runRepair(input, output, null);
        }
    }

    private void runRepair(Path input, Path output, char[] password) {
        startBusy(Messages.get("status.repairing"));
        session.repair(input, output, password)
                .whenComplete((report, error) -> Platform.runLater(() -> finishRepair(input, output, report, error)));
    }

    private void finishRepair(Path input, Path output, PdfRepairer.Report report, Throwable error) {
        endBusy();
        if (error == null) {
            notice = Messages.format("status.repaired", report.pagesRecovered(), report.pagesFound());
            refreshState();
            Dialogs.showRepairReport(stage, report);
            return;
        }
        Throwable cause = causeOf(error);
        if (cause instanceof PasswordProtectedException) {
            Path name = input.getFileName();
            PasswordDialog.show(stage, name == null ? "" : name.toString(), cause instanceof WrongPasswordException)
                    .ifPresent(password -> runRepair(input, output, password));
            return;
        }
        Dialogs.showError(stage, messageOf(error, Messages.FILE_UNREADABLE));
    }

    private void protect() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        ProtectDialog.show(stage).ifPresent(password -> {
            SaveJob job = SaveJob.of("output.suffix.protected", new ProtectStep(password));
            Arrays.fill(password, '\0');
            startSaving(job);
        });
    }

    private void askNextPassword() {
        ImportResult.Failure failure = passwordQueue.poll();
        if (failure == null) {
            return;
        }
        boolean retry = failure.reason() == ImportResult.Reason.PASSWORD_WRONG;
        Optional<char[]> password = PasswordDialog.show(stage, failure.fileName(), retry);
        if (password.isEmpty()) {
            askNextPassword();
            return;
        }
        startBusy(Messages.get("status.importing"));
        session.importProtected(failure.file(), password.get())
                .whenComplete((result, error) -> Platform.runLater(() -> finishImport(result, error)));
    }

    private void exportImages() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        ExportImagesDialog.show(stage).ifPresent(options -> startSaving(SaveJob.exportImages(options)));
    }

    private void crop() {
        if (busy || session.items().isEmpty()) {
            return;
        }
        CropDialog.show(stage, session).ifPresent(options ->
                startSaving(SaveJob.of("output.suffix.cropped", new CropStep(options))));
    }

    private static boolean isSupportedWatermarkImage(Path image) {
        try {
            FileType type = new FileTypeDetector().detect(image);
            return type == FileType.JPEG || type == FileType.PNG;
        } catch (PdfLocalException e) {
            return false;
        }
    }

    private void startSaving(SaveJob job) {
        if (busy || session.items().isEmpty()) {
            return;
        }
        if (job.export() == null && session.hasSignedSources() && !Dialogs.confirmSignatureLoss(stage)) {
            return;
        }
        String extension = outputExtension(job);
        String suggestedName = suggestedOutputName(job, extension);
        Path output = job.export() == null
                ? Dialogs.chooseOutput(stage, lastDirectory, suggestedName)
                : Dialogs.chooseExportOutput(stage, lastDirectory, suggestedName, extension);
        if (output == null) {
            return;
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
        long originalBytes = job.compressing() ? session.sourceBytes() : 0;
        startBusy(Messages.get("status.saving"));
        grid.setEditable(false);
        progress.setProgress(0);
        cancelRequested = false;
        savingJob = job;
        AtomicInteger lastPercent = new AtomicInteger(-1);
        AtomicInteger lastStepPercent = new AtomicInteger(-1);
        BiConsumer<Integer, Integer> onPage = (done, total) -> {
            int percent = done * 100 / total;
            if (lastPercent.getAndSet(percent) != percent) {
                Platform.runLater(() -> showSaveProgress(done, total));
            }
        };
        BiConsumer<Integer, Integer> onStep = (done, total) -> {
            int percent = done * 100 / Math.max(1, total);
            if (lastStepPercent.getAndSet(percent) != percent) {
                Platform.runLater(() -> showStepProgress(done, total));
            }
        };
        saving = job.export() == null
                ? session.startSave(output, toolbar.pageSize(), job.steps(), onPage, onStep)
                : session.startExport(output, toolbar.pageSize(), job.export(), onPage, onStep);
        refreshState();
        saving.result().whenComplete((result, error) ->
                Platform.runLater(() -> finishSave(result, error, originalBytes)));
    }

    private String outputExtension(SaveJob job) {
        if (job.export() == null) {
            return "pdf";
        }
        return session.items().size() == 1 ? job.export().format().extension() : "zip";
    }

    private String suggestedOutputName(SaveJob job, String extension) {
        String suffixKey = job.suffixKey() != null ? job.suffixKey()
                : session.fileCount() == 1 ? "output.suffix.edited" : "output.suffix.merged";
        return OutputNames.suggest(session.displayName(session.items().get(0)), Messages.get(suffixKey), extension);
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
            busyMessage = Messages.get(savingJob.afterPagesKey());
        } else {
            progress.setProgress(done / (double) total);
            busyMessage = Messages.format("status.saving.progress", done, total);
        }
        refreshState();
    }

    private void showStepProgress(int done, int total) {
        if (saving == null || cancelRequested) {
            return;
        }
        if (done >= total) {
            progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            busyMessage = Messages.get("status.saving.finishing");
        } else {
            progress.setProgress(done / (double) total);
            busyMessage = Messages.format(savingJob.stepKey(), done, total);
        }
        refreshState();
    }

    private void finishSave(SaveResult result, Throwable error, long originalBytes) {
        saving = null;
        grid.setEditable(true);
        endBusy();
        if (error == null) {
            notice = successNotice(result, originalBytes);
        } else if (causeOf(error) instanceof SaveCancelledException) {
            notice = Messages.get(Messages.SAVE_CANCELLED);
        } else {
            Dialogs.showError(stage, messageOf(error, Messages.SAVE_FAILED));
        }
        refreshState();
    }

    private String successNotice(SaveResult result, long originalBytes) {
        if (savingJob.export() != null) {
            return result.pages() == 1
                    ? Messages.format("status.exported.single", FileSizes.format(result.outputBytes()))
                    : Messages.format("status.exported.zip", result.pages(), FileSizes.format(result.outputBytes()));
        }
        if (originalBytes <= 0) {
            return Messages.format("status.saved", result.pages());
        }
        if (result.outputBytes() >= originalBytes) {
            return Messages.format("status.compressed.same", result.pages(), FileSizes.format(result.outputBytes()));
        }
        long percent = Math.round(100.0 * (originalBytes - result.outputBytes()) / originalBytes);
        return Messages.format("status.compressed", FileSizes.format(originalBytes),
                FileSizes.format(result.outputBytes()), percent);
    }

    private void finishImport(ImportResult result, Throwable error) {
        endBusy();
        if (error != null) {
            LOG.log(Level.WARNING, "Import failed: {0}", error.getClass().getSimpleName());
            Dialogs.showError(stage, Messages.get(Messages.UNEXPECTED));
            return;
        }
        session.append(result.items());
        List<ImportResult.Failure> others = new ArrayList<>();
        for (ImportResult.Failure failure : result.failures()) {
            if (failure.needsPassword()) {
                passwordQueue.add(failure);
            } else {
                others.add(failure);
            }
        }
        if (!others.isEmpty()) {
            Dialogs.showImportFailures(stage, others);
        }
        askNextPassword();
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
