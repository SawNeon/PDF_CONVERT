package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageNumberOptions;
import br.com.pdflocal.util.Messages;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class PageNumbersDialog {

    private static final double WIDTH = 480;
    private static final List<Integer> FONT_SIZES = List.of(8, 10, 12, 14, 18);
    private static final int EXAMPLE_NUMBER = 1;
    private static final int EXAMPLE_TOTAL = 10;

    private PageNumbersDialog() {
    }

    static Optional<PageNumberOptions> show(Window owner) {
        ChoiceBox<PageNumberOptions.Position> position = new ChoiceBox<>();
        position.getItems().addAll(PageNumberOptions.Position.values());
        position.setValue(PageNumberOptions.Position.BOTTOM_CENTER);
        position.setConverter(new StringConverter<>() {
            @Override
            public String toString(PageNumberOptions.Position value) {
                return value == null ? "" : Messages.get("numbers.position." + value.name().toLowerCase().replace('_', '-'));
            }

            @Override
            public PageNumberOptions.Position fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });

        ChoiceBox<String> format = new ChoiceBox<>();
        for (int i = 1; i <= 4; i++) {
            format.getItems().add(Messages.get("numbers.format." + i));
        }
        format.setValue(format.getItems().get(2));
        format.setConverter(new StringConverter<>() {
            @Override
            public String toString(String pattern) {
                return pattern == null ? "" : pattern.replace(PageNumberOptions.NUMBER_TOKEN, String.valueOf(EXAMPLE_NUMBER))
                        .replace(PageNumberOptions.TOTAL_TOKEN, String.valueOf(EXAMPLE_TOTAL));
            }

            @Override
            public String fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });

        Spinner<Integer> start = new Spinner<>(1, PageNumberOptions.MAX_START_NUMBER, 1);
        start.setEditable(true);
        start.setPrefWidth(110);
        CheckBox skipFirst = new CheckBox(Messages.get("numbers.skip-first"));
        ChoiceBox<Integer> size = new ChoiceBox<>();
        size.getItems().addAll(FONT_SIZES);
        size.setValue(10);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label(Messages.get("numbers.position")), position);
        form.addRow(1, new Label(Messages.get("numbers.format")), format);
        form.addRow(2, new Label(Messages.get("numbers.start")), start);
        form.addRow(3, new Label(Messages.get("numbers.size")), size);
        form.add(skipFirst, 1, 4);

        Label intro = new Label(Messages.get("numbers.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(WIDTH - 16);
        intro.getStyleClass().add("dialog-intro");
        VBox content = new VBox(14, intro, form);
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<PageNumberOptions> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("numbers.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            start.increment(0);
            return new PageNumberOptions(position.getValue(), format.getValue(), start.getValue(),
                    skipFirst.isSelected(), size.getValue());
        });
        return dialog.showAndWait();
    }
}
