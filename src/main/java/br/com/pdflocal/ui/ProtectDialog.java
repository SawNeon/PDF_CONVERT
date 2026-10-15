package br.com.pdflocal.ui;

import br.com.pdflocal.service.ProtectStep;
import br.com.pdflocal.util.Messages;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class ProtectDialog {

    private static final double WIDTH = 500;

    private ProtectDialog() {
    }

    static Optional<char[]> show(Window owner) {
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        password.setPrefColumnCount(22);
        confirm.setPrefColumnCount(22);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label(Messages.get("protect.password")), password);
        form.addRow(1, new Label(Messages.get("protect.confirm")), confirm);

        Label rule = label(Messages.get("protect.rule"), "option-detail");
        Label mismatch = label(Messages.get("protect.mismatch"), "dialog-notice");
        BooleanBinding tooShort = Bindings.createBooleanBinding(
                () -> password.getText().length() < ProtectStep.MIN_PASSWORD_LENGTH, password.textProperty());
        BooleanBinding different = Bindings.createBooleanBinding(
                () -> !confirm.getText().isEmpty() && !confirm.getText().equals(password.getText()),
                password.textProperty(), confirm.textProperty());
        mismatch.visibleProperty().bind(different);

        VBox content = new VBox(12, label(Messages.get("protect.intro"), "dialog-intro"), form, rule, mismatch,
                label(Messages.get("protect.warning"), "option-detail"));
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType save = new ButtonType(Messages.get("dialog.save-as"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("protect.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(tooShort.or(Bindings.createBooleanBinding(
                () -> !confirm.getText().equals(password.getText()), password.textProperty(), confirm.textProperty())));
        dialog.setOnShown(event -> password.requestFocus());
        dialog.setResultConverter(button -> button == save ? password.getText().toCharArray() : null);
        return dialog.showAndWait();
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setPrefWidth(WIDTH - 16);
        label.setMaxWidth(WIDTH - 16);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
