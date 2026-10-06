package br.com.pdflocal.ui;

import atlantafx.base.theme.Styles;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.ViewMode;
import br.com.pdflocal.util.Messages;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

final class Toolbar extends ToolBar {

    private final Button add = new Button(Messages.get("toolbar.add"));
    private final Button clear = new Button(Messages.get("toolbar.clear"));
    private final Button save = new Button(Messages.get("toolbar.save"));
    private final Button themeButton = new Button();
    private final ToggleButton byFile = new ToggleButton(Messages.get("toolbar.view.file"));
    private final ToggleButton byPage = new ToggleButton(Messages.get("toolbar.view.page"));
    private final ChoiceBox<PageSize> pageSize = new ChoiceBox<>(FXCollections.observableArrayList(PageSize.values()));

    Toolbar(Runnable onAdd, Runnable onClear, Runnable onSave, Consumer<ViewMode> onViewMode,
            Runnable onToggleTheme, Theme theme) {
        add.setOnAction(event -> onAdd.run());
        clear.setOnAction(event -> onClear.run());
        save.setOnAction(event -> onSave.run());
        themeButton.setOnAction(event -> onToggleTheme.run());
        setTheme(theme);
        save.getStyleClass().add("save-button");

        ToggleGroup views = new ToggleGroup();
        byFile.setToggleGroup(views);
        byPage.setToggleGroup(views);
        byFile.getStyleClass().add(Styles.LEFT_PILL);
        byPage.getStyleClass().add(Styles.RIGHT_PILL);
        byPage.setSelected(true);
        views.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                previous.setSelected(true);
            } else {
                onViewMode.accept(current == byFile ? ViewMode.BY_FILE : ViewMode.BY_PAGE);
            }
        });

        pageSize.setConverter(new StringConverter<>() {
            @Override
            public String toString(PageSize size) {
                return size == null ? "" : Messages.get(size == PageSize.A4 ? "pagesize.a4" : "pagesize.original");
            }

            @Override
            public PageSize fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        pageSize.setValue(PageSize.A4);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getItems().addAll(add, clear, new Separator(), byFile, byPage, new Separator(),
                new Label(Messages.get("toolbar.pagesize.label")), pageSize, spacer, themeButton, save);
        setState(false, false);
    }

    void setTheme(Theme theme) {
        themeButton.setText(Messages.get(theme == Theme.DARK ? "toolbar.theme.light" : "toolbar.theme.dark"));
    }

    PageSize pageSize() {
        return pageSize.getValue();
    }

    void setState(boolean busy, boolean hasPages) {
        add.setDisable(busy);
        clear.setDisable(busy || !hasPages);
        save.setDisable(busy || !hasPages);
        byFile.setDisable(busy);
        byPage.setDisable(busy);
        pageSize.setDisable(busy);
    }
}
