package br.com.pdflocal.ui;

import br.com.pdflocal.service.FormFiller;
import br.com.pdflocal.util.Messages;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class FormDialog {

    record Answers(Map<String, String> changes, boolean flatten) {
    }

    private static final double WIDTH = 560;
    private static final double LIST_HEIGHT = 380;
    private static final int MULTILINE_ROWS = 3;

    private FormDialog() {
    }

    static Optional<Answers> show(Window owner, String fileName, FormFiller.FormInfo info) {
        VBox fields = new VBox(12);
        fields.setPadding(new Insets(4, 12, 4, 4));
        Map<String, Supplier<String>> readers = new LinkedHashMap<>();
        Map<String, String> originals = new LinkedHashMap<>();
        BooleanProperty touched = new SimpleBooleanProperty();

        for (FormFiller.Field field : info.fields()) {
            Label label = new Label(field.name());
            label.setWrapText(true);
            Node control = control(field, readers, touched);
            originals.put(field.name(), field.value());
            fields.getChildren().add(new VBox(3, label, control));
        }
        ScrollPane scroll = new ScrollPane(fields);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(LIST_HEIGHT);
        scroll.setMinViewportHeight(LIST_HEIGHT);

        CheckBox flatten = new CheckBox(Messages.get("form.flatten"));
        Label flattenDetail = wrapped(Messages.get("form.flatten.detail"), "option-detail");
        Label intro = wrapped(Messages.format("form.intro", fileName), "dialog-intro");
        VBox content = new VBox(12, intro, scroll, flatten, flattenDetail);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<Answers> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("form.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !touched.get() && !flatten.isSelected(), touched, flatten.selectedProperty()));
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            Map<String, String> changes = new LinkedHashMap<>();
            readers.forEach((name, reader) -> {
                String value = reader.get();
                if (!value.equals(originals.get(name))) {
                    changes.put(name, value);
                }
            });
            return new Answers(changes, flatten.isSelected());
        });
        return dialog.showAndWait();
    }

    private static Node control(FormFiller.Field field, Map<String, Supplier<String>> readers,
                                BooleanProperty touched) {
        switch (field.type()) {
            case CHECKBOX -> {
                CheckBox box = new CheckBox();
                box.setSelected(FormFiller.CHECKED.equals(field.value()));
                box.selectedProperty().addListener((observable, before, now) -> touched.set(true));
                readers.put(field.name(), () -> box.isSelected() ? FormFiller.CHECKED : FormFiller.UNCHECKED);
                return box;
            }
            case RADIO, CHOICE -> {
                ChoiceBox<String> choice = new ChoiceBox<>();
                choice.getItems().add("");
                choice.getItems().addAll(field.options());
                choice.setValue(choice.getItems().contains(field.value()) ? field.value() : "");
                choice.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(String value) {
                        return value == null || value.isEmpty() ? Messages.get("form.choice.empty") : value;
                    }

                    @Override
                    public String fromString(String text) {
                        throw new UnsupportedOperationException();
                    }
                });
                choice.valueProperty().addListener((observable, before, now) -> touched.set(true));
                readers.put(field.name(), () -> choice.getValue() == null ? "" : choice.getValue());
                return choice;
            }
            default -> {
                if (field.multiline()) {
                    TextArea area = new TextArea(field.value());
                    area.setPrefRowCount(MULTILINE_ROWS);
                    limit(area, field.maxLength());
                    area.textProperty().addListener((observable, before, now) -> touched.set(true));
                    readers.put(field.name(), area::getText);
                    return area;
                }
                TextField text = new TextField(field.value());
                limit(text, field.maxLength());
                text.textProperty().addListener((observable, before, now) -> touched.set(true));
                readers.put(field.name(), text::getText);
                return text;
            }
        }
    }

    private static void limit(javafx.scene.control.TextInputControl control, int maxLength) {
        if (maxLength > 0) {
            control.setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().length() <= maxLength ? change : null));
        }
    }

    private static Label wrapped(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setPrefWidth(WIDTH - 16);
        label.setMaxWidth(WIDTH - 16);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
