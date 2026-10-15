package br.com.pdflocal.ui;

import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.util.Messages;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class ExportImagesDialog {

    private static final double WIDTH = 520;
    private static final List<Integer> RESOLUTIONS = List.of(100, 150, 200, 300);
    private static final int DEFAULT_RESOLUTION = 150;

    private ExportImagesDialog() {
    }

    static Optional<ImageExportOptions> show(Window owner) {
        ToggleGroup formats = new ToggleGroup();
        RadioButton jpeg = new RadioButton(Messages.get("export.format.jpeg"));
        RadioButton png = new RadioButton(Messages.get("export.format.png"));
        jpeg.setToggleGroup(formats);
        png.setToggleGroup(formats);
        jpeg.setSelected(true);

        ChoiceBox<Integer> resolution = new ChoiceBox<>();
        resolution.getItems().addAll(RESOLUTIONS);
        resolution.setValue(DEFAULT_RESOLUTION);
        resolution.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer dpi) {
                return dpi == null ? "" : Messages.get("export.resolution." + dpi);
            }

            @Override
            public Integer fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label(Messages.get("export.format")), 0, 0);
        form.add(new VBox(6, jpeg, png), 1, 0);
        form.add(new Label(Messages.get("export.resolution")), 0, 1);
        form.add(resolution, 1, 1);

        Label intro = new Label(Messages.get("export.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(WIDTH - 16);
        intro.getStyleClass().add("dialog-intro");
        VBox content = new VBox(14, intro, form);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<ImageExportOptions> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("export.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.setResultConverter(button -> button != save ? null : new ImageExportOptions(
                jpeg.isSelected() ? ImageExportOptions.Format.JPEG : ImageExportOptions.Format.PNG,
                resolution.getValue()));
        return dialog.showAndWait();
    }
}
