package br.com.pdflocal.ui;

import br.com.pdflocal.model.CropOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.util.Messages;
import java.util.List;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class CropDialog {

    private static final double WIDTH = 600;
    private static final double PICKER_WIDTH = 540;
    private static final double PICKER_HEIGHT = 420;
    private static final double DETAIL_INDENT = 26;

    private CropDialog() {
    }

    static Optional<CropOptions> show(Window owner, DocumentSession session) {
        List<PageItem> items = List.copyOf(session.items());
        AtomicInteger current = new AtomicInteger();
        AtomicInteger loadToken = new AtomicInteger();

        RegionPickerPane picker = new RegionPickerPane(PICKER_WIDTH, PICKER_HEIGHT);
        Label loading = new Label(Messages.get("crop.loading"));
        loading.getStyleClass().add("option-detail");
        StackPane pickerHolder = new StackPane(picker, loading);
        pickerHolder.setMinSize(PICKER_WIDTH, PICKER_HEIGHT);
        pickerHolder.setAlignment(Pos.CENTER);

        Label pageLabel = new Label();
        Button previous = new Button(Messages.get("preview.previous"));
        Button next = new Button(Messages.get("preview.next"));
        Button clear = new Button(Messages.get("crop.clear"));
        Label hint = new Label(Messages.get("crop.hint"));
        hint.getStyleClass().add("option-detail");

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
                    picker.show(image, item.rotation());
                }
            }));
        };
        previous.setOnAction(event -> {
            current.decrementAndGet();
            showPage.run();
        });
        next.setOnAction(event -> {
            current.incrementAndGet();
            showPage.run();
        });
        clear.setOnAction(event -> picker.clear());
        HBox navigation = new HBox(10, previous, pageLabel, next, clear);
        navigation.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup modes = new ToggleGroup();
        RadioButton remove = new RadioButton(Messages.get("crop.mode.remove"));
        RadioButton hide = new RadioButton(Messages.get("crop.mode.hide"));
        remove.setToggleGroup(modes);
        hide.setToggleGroup(modes);
        remove.setSelected(true);
        VBox options = new VBox(10, option(remove, "crop.mode.remove.detail"), option(hide, "crop.mode.hide.detail"));

        Label intro = new Label(Messages.get("crop.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(WIDTH - 16);
        intro.getStyleClass().add("dialog-intro");
        VBox content = new VBox(12, intro, navigation, pickerHolder, hint, options);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<CropOptions> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("crop.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.setDisable(true);
        picker.setOnChange(region -> {
            saveButton.setDisable(region.isEmpty());
            hint.setText(Messages.get(region.isPresent() ? "crop.hint.ready" : "crop.hint"));
        });
        dialog.setResultConverter(button -> {
            if (button != save || picker.region().isEmpty()) {
                return null;
            }
            NormalizedRegion region = picker.region().get();
            return new CropOptions(region.left(), region.top(), region.right(), region.bottom(),
                    remove.isSelected() ? CropOptions.Mode.REMOVE_OUTSIDE : CropOptions.Mode.HIDE_OUTSIDE);
        });
        showPage.run();
        return dialog.showAndWait();
    }

    private static VBox option(RadioButton radio, String detailKey) {
        Label detail = new Label(Messages.get(detailKey));
        detail.getStyleClass().add("option-detail");
        detail.setWrapText(true);
        detail.setMaxWidth(WIDTH - 16 - DETAIL_INDENT);
        VBox.setMargin(detail, new Insets(0, 0, 0, DETAIL_INDENT));
        return new VBox(3, radio, detail);
    }
}
