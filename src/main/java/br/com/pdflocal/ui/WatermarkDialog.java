package br.com.pdflocal.ui;

import br.com.pdflocal.model.WatermarkOptions;
import br.com.pdflocal.service.StandardText;
import br.com.pdflocal.util.Messages;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

final class WatermarkDialog {

    private static final double WIDTH = 500;
    private static final double DEFAULT_OPACITY_PERCENT = 30;

    private WatermarkDialog() {
    }

    static Optional<WatermarkOptions> show(Window owner, Path initialDirectory) {
        ToggleGroup mode = new ToggleGroup();
        RadioButton textMode = new RadioButton(Messages.get("watermark.mode.text"));
        RadioButton imageMode = new RadioButton(Messages.get("watermark.mode.image"));
        textMode.setToggleGroup(mode);
        imageMode.setToggleGroup(mode);
        textMode.setSelected(true);

        TextField text = new TextField(Messages.get("watermark.text.default"));
        text.setPrefColumnCount(24);
        text.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= WatermarkOptions.MAX_TEXT_LENGTH ? change : null));
        Label textProblem = new Label(Messages.get("watermark.text.unsupported"));
        textProblem.getStyleClass().add("dialog-notice");
        textProblem.setWrapText(true);
        textProblem.setMaxWidth(WIDTH - 16);

        ObjectProperty<Path> image = new SimpleObjectProperty<>();
        Button choose = new Button(Messages.get("watermark.image.choose"));
        Label imageName = new Label(Messages.get("watermark.image.none"));
        imageName.getStyleClass().add("option-detail");
        choose.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Messages.get("watermark.image.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    Messages.get("watermark.image.filter"), "*.jpg", "*.jpeg", "*.png"));
            if (initialDirectory != null && initialDirectory.toFile().isDirectory()) {
                chooser.setInitialDirectory(initialDirectory.toFile());
            }
            File file = chooser.showOpenDialog(owner);
            if (file != null) {
                image.set(file.toPath());
                imageName.setText(file.getName());
            }
        });

        Slider opacity = new Slider(WatermarkOptions.MIN_OPACITY * 100, WatermarkOptions.MAX_OPACITY * 100,
                DEFAULT_OPACITY_PERCENT);
        Label opacityLabel = new Label();
        opacityLabel.textProperty().bind(Bindings.createStringBinding(
                () -> Messages.format("watermark.opacity", Math.round(opacity.getValue())), opacity.valueProperty()));
        CheckBox diagonal = new CheckBox(Messages.get("watermark.diagonal"));
        diagonal.setSelected(true);

        VBox textBox = new VBox(6, text, textProblem);
        HBox imageBox = new HBox(10, choose, imageName);
        textBox.visibleProperty().bind(textMode.selectedProperty());
        imageBox.visibleProperty().bind(imageMode.selectedProperty());
        imageBox.setAlignment(Pos.TOP_LEFT);
        StackPane source = new StackPane(textBox, imageBox);
        source.setAlignment(Pos.TOP_LEFT);
        textProblem.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !text.getText().isBlank() && !StandardText.canEncode(text.getText()), text.textProperty()));

        Label intro = new Label(Messages.get("watermark.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(WIDTH - 16);
        intro.getStyleClass().add("dialog-intro");
        VBox content = new VBox(12, intro, new HBox(16, textMode, imageMode), source, opacityLabel, opacity,
                diagonal);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<WatermarkOptions> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("watermark.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> textMode.isSelected()
                        ? text.getText().isBlank() || !StandardText.canEncode(text.getText())
                        : image.get() == null,
                textMode.selectedProperty(), text.textProperty(), image));
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            double value = opacity.getValue() / 100.0;
            return textMode.isSelected()
                    ? WatermarkOptions.ofText(text.getText(), value, diagonal.isSelected())
                    : WatermarkOptions.ofImage(image.get(), value, diagonal.isSelected());
        });
        return dialog.showAndWait();
    }
}
