package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.RedactionArea;
import br.com.pdflocal.util.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class RedactDialog {

    private static final double WIDTH = 600;
    private static final double PICKER_WIDTH = 540;
    private static final double PICKER_HEIGHT = 420;

    private RedactDialog() {
    }

    static Optional<Map<Integer, List<RedactionArea>>> show(Window owner, DocumentSession session) {
        List<PageItem> items = List.copyOf(session.items());
        Map<Integer, List<NormalizedRegion>> marked = new HashMap<>();
        AtomicInteger current = new AtomicInteger();
        AtomicInteger loadToken = new AtomicInteger();
        AtomicInteger displayed = new AtomicInteger(-1);

        AreasPickerPane picker = new AreasPickerPane(PICKER_WIDTH, PICKER_HEIGHT);
        Label loading = new Label(Messages.get("crop.loading"));
        loading.getStyleClass().add("option-detail");
        StackPane holder = new StackPane(picker, loading);
        holder.setMinSize(PICKER_WIDTH, PICKER_HEIGHT);
        holder.setAlignment(Pos.CENTER);

        Label pageLabel = new Label();
        Button previous = new Button(Messages.get("preview.previous"));
        Button next = new Button(Messages.get("preview.next"));
        Button undo = new Button(Messages.get("redact.undo"));
        Button clear = new Button(Messages.get("redact.clear-page"));
        Label summary = new Label();
        summary.getStyleClass().add("option-detail");

        Runnable refreshSummary = () -> {
            if (displayed.get() >= 0) {
                marked.put(displayed.get(), picker.areas());
            }
            marked.values().removeIf(List::isEmpty);
            int total = marked.values().stream().mapToInt(List::size).sum();
            summary.setText(total == 0 ? Messages.get("redact.none")
                    : Messages.format("redact.summary", total, marked.size()));
        };
        picker.setOnChange(refreshSummary);

        Runnable showPage = () -> {
            int index = current.get();
            int token = loadToken.incrementAndGet();
            pageLabel.setText(Messages.format("crop.page", index + 1, items.size()));
            previous.setDisable(index == 0);
            next.setDisable(index >= items.size() - 1);
            loading.setVisible(true);
            PageItem item = items.get(index);
            session.preview(item).whenComplete((image, error) -> Platform.runLater(() -> {
                if (token != loadToken.get()) {
                    return;
                }
                loading.setVisible(false);
                if (error == null) {
                    displayed.set(index);
                    picker.show(image, item.rotation(), marked.getOrDefault(index, List.of()));
                }
            }));
        };
        previous.setOnAction(event -> {
            keepDisplayedAreas(marked, displayed, picker);
            current.decrementAndGet();
            showPage.run();
        });
        next.setOnAction(event -> {
            keepDisplayedAreas(marked, displayed, picker);
            current.incrementAndGet();
            showPage.run();
        });
        undo.setOnAction(event -> picker.undoLast());
        clear.setOnAction(event -> picker.clear());
        HBox navigation = new HBox(10, previous, pageLabel, next, undo, clear);
        navigation.setAlignment(Pos.CENTER_LEFT);

        Label intro = wrapped(Messages.get("redact.intro"), "dialog-intro");
        Label warning = wrapped(Messages.get("redact.warning"), "option-detail");
        VBox content = new VBox(12, intro, navigation, holder, summary, warning);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType apply = new ButtonType(Messages.get("redact.apply"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<Map<Integer, List<RedactionArea>>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("redact.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(apply, cancel);
        Node applyButton = dialog.getDialogPane().lookupButton(apply);
        applyButton.setDisable(true);
        picker.setOnChange(() -> {
            refreshSummary.run();
            applyButton.setDisable(marked.isEmpty());
        });
        dialog.setResultConverter(button -> {
            if (button != apply) {
                return null;
            }
            keepDisplayedAreas(marked, displayed, picker);
            Map<Integer, List<RedactionArea>> result = new HashMap<>();
            marked.forEach((page, regions) -> {
                List<RedactionArea> areas = new ArrayList<>();
                for (NormalizedRegion region : regions) {
                    areas.add(new RedactionArea(region.left(), region.top(), region.right(), region.bottom()));
                }
                if (!areas.isEmpty()) {
                    result.put(page, areas);
                }
            });
            return result.isEmpty() ? null : result;
        });
        refreshSummary.run();
        showPage.run();
        return dialog.showAndWait();
    }

    private static void keepDisplayedAreas(Map<Integer, List<NormalizedRegion>> marked, AtomicInteger displayed,
                                           AreasPickerPane picker) {
        if (displayed.get() >= 0) {
            marked.put(displayed.get(), picker.areas());
        }
    }

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setPrefWidth(WIDTH - 16);
        label.setMaxWidth(WIDTH - 16);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }
}
