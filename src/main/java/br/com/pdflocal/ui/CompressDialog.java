package br.com.pdflocal.ui;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.service.ImageShare;
import br.com.pdflocal.util.FileSizes;
import br.com.pdflocal.util.Messages;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class CompressDialog {

    private static final double WIDTH = 540;
    private static final double DETAIL_INDENT = 26;

    private CompressDialog() {
    }

    static Optional<CompressionLevel> show(Window owner, int pages, int files, long currentBytes, ImageShare share) {
        Label intro = label(Messages.get("dialog.compress.intro"), "dialog-intro");
        Label summary = label(Messages.format("dialog.compress.summary", pages, files, FileSizes.format(currentBytes)),
                "option-detail");

        ToggleGroup group = new ToggleGroup();
        VBox options = new VBox(12,
                option(group, CompressionLevel.LIGHT, "light"),
                option(group, CompressionLevel.MEDIUM, "medium"),
                option(group, CompressionLevel.STRONG, "strong"));
        group.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == CompressionLevel.MEDIUM)
                .findFirst()
                .ifPresent(group::selectToggle);

        Label warning = label(Messages.get("dialog.compress.warning"), "option-detail");

        VBox content = new VBox(14, intro, summary);
        if (share != null && share.isLow()) {
            content.getChildren().add(label(Messages.format("dialog.compress.low-gain", share.percent()), "dialog-notice"));
        }
        content.getChildren().addAll(options, warning);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.compress.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<CompressionLevel> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("dialog.compress.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.setResultConverter(button ->
                button == save ? (CompressionLevel) group.getSelectedToggle().getUserData() : null);
        return dialog.showAndWait();
    }

    private static VBox option(ToggleGroup group, CompressionLevel level, String key) {
        RadioButton radio = new RadioButton(Messages.get("dialog.compress.level." + key));
        radio.setToggleGroup(group);
        radio.setUserData(level);
        Label detail = label(Messages.get("dialog.compress.level." + key + ".detail"), "option-detail");
        VBox.setMargin(detail, new Insets(0, 0, 0, DETAIL_INDENT));
        return new VBox(3, radio, detail);
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMaxWidth(WIDTH - 16);
        return label;
    }
}
