package br.com.pdflocal.ui;

import br.com.pdflocal.util.Messages;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

final class PasswordDialog {

    private static final double WIDTH = 480;

    private PasswordDialog() {
    }

    static Optional<char[]> show(Window owner, String fileName, boolean retry) {
        Label prompt = label(Messages.format("password.prompt", fileName), "dialog-intro");
        PasswordField password = new PasswordField();
        password.setPromptText(Messages.get("password.label"));
        VBox content = new VBox(12, prompt);
        if (retry) {
            content.getChildren().add(label(Messages.get("password.retry"), "dialog-notice"));
        }
        content.getChildren().addAll(password, label(Messages.get("password.warning"), "option-detail"));
        content.setPadding(new Insets(8));
        content.setPrefWidth(WIDTH);

        ButtonType open = new ButtonType(Messages.get("password.open"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.get("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Appearance.style(dialog);
        dialog.setTitle(Messages.get("password.title"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(open, cancel);
        Node openButton = dialog.getDialogPane().lookupButton(open);
        openButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> password.getText().isEmpty(), password.textProperty()));
        dialog.setOnShown(event -> password.requestFocus());
        dialog.setResultConverter(button -> button == open ? password.getText().toCharArray() : null);
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
