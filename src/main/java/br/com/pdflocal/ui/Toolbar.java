package br.com.pdflocal.ui;

import atlantafx.base.theme.Styles;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.ViewMode;
import br.com.pdflocal.util.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

final class Toolbar extends ToolBar {

    private static final double BRAND_WIDTH = 40;
    private static final double BRAND_HEIGHT = 26;

    private final Button add = new Button(Messages.get("toolbar.add"));
    private final Button clear = new Button(Messages.get("toolbar.clear"));
    private final Button save = new Button(Messages.get("toolbar.save"));
    private final Button themeButton = new Button();
    private final MenuButton tools = new MenuButton(Messages.get("toolbar.tools"));
    private final List<Map.Entry<MenuItem, Tool>> toolItems = new ArrayList<>();
    private final ToggleButton byFile = new ToggleButton(Messages.get("toolbar.view.file"));
    private final ToggleButton byPage = new ToggleButton(Messages.get("toolbar.view.page"));
    private final ChoiceBox<PageSize> pageSize = new ChoiceBox<>(FXCollections.observableArrayList(PageSize.values()));

    Toolbar(Runnable onAdd, Runnable onClear, Runnable onSave, List<Tool> toolList, Consumer<ViewMode> onViewMode,
            Runnable onToggleTheme, Theme theme, Runnable onAbout) {
        add.setOnAction(event -> onAdd.run());
        clear.setOnAction(event -> onClear.run());
        save.setOnAction(event -> onSave.run());
        buildToolsMenu(toolList);
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

        Region brand = new Region();
        brand.getStyleClass().add("brand-mark");
        brand.setMinSize(BRAND_WIDTH, BRAND_HEIGHT);
        brand.setPrefSize(BRAND_WIDTH, BRAND_HEIGHT);
        brand.setMaxSize(BRAND_WIDTH, BRAND_HEIGHT);
        Tooltip.install(brand, new Tooltip(Messages.get("toolbar.about.tooltip")));
        brand.setOnMouseClicked(event -> onAbout.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getItems().addAll(brand, add, clear, new Separator(), byFile, byPage, new Separator(),
                new Label(Messages.get("toolbar.pagesize.label")), pageSize, spacer, themeButton, tools, save);
        setState(false, false);
    }

    private void buildToolsMenu(List<Tool> toolList) {
        for (ToolGroup group : ToolGroup.values()) {
            List<Tool> inGroup = toolList.stream().filter(tool -> tool.group() == group).toList();
            if (inGroup.isEmpty()) {
                continue;
            }
            if (!tools.getItems().isEmpty()) {
                tools.getItems().add(new SeparatorMenuItem());
            }
            Label heading = new Label(Messages.get(group.messageKey()));
            heading.getStyleClass().add("tool-heading");
            tools.getItems().add(new CustomMenuItem(heading, false));
            for (Tool tool : inGroup) {
                MenuItem item = new MenuItem(Messages.get(tool.titleKey()));
                item.setOnAction(event -> tool.action().run());
                tools.getItems().add(item);
                toolItems.add(Map.entry(item, tool));
            }
        }
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
        tools.setDisable(busy);
        toolItems.forEach(entry -> entry.getKey().setDisable(entry.getValue().requiresPages() && !hasPages));
        byFile.setDisable(busy);
        byPage.setDisable(busy);
        pageSize.setDisable(busy);
    }
}
