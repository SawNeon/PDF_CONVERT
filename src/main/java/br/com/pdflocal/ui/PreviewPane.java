package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.util.Messages;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

final class PreviewPane extends BorderPane {

    private static final double INITIAL_WIDTH = 920;
    private static final double INITIAL_HEIGHT = 720;
    private static final double VIEWPORT_MARGIN = 2;

    private final DocumentSession session;
    private final List<PageItem> items;
    private final Runnable onClose;
    private final ZoomModel zoom = new ZoomModel();
    private final StringProperty title = new SimpleStringProperty();
    private final ImageView imageView = new ImageView();
    private final StackPane canvas = new StackPane(new Group(imageView));
    private final ScrollPane scroll = new ScrollPane(canvas);
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label failure = new Label(Messages.get("preview.failed"));
    private final Label pageLabel = new Label();
    private final Label zoomLabel = new Label();
    private final Button previous = new Button(Messages.get("preview.previous"));
    private final Button next = new Button(Messages.get("preview.next"));

    private int index;
    private int generation;
    private Image image;
    private double currentScale = 1.0;

    private PreviewPane(DocumentSession session, List<PageItem> items, int startIndex, Runnable onClose) {
        this.session = session;
        this.items = items;
        this.onClose = onClose;

        imageView.setSmooth(true);
        imageView.setPreserveRatio(false);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("preview-scroll");
        canvas.setAlignment(Pos.CENTER);
        canvas.setPadding(new Insets(0));

        progress.setMaxSize(48, 48);
        progress.setVisible(false);
        failure.getStyleClass().add("empty-hint");
        failure.setVisible(false);
        StackPane center = new StackPane(scroll, progress, failure);
        center.getStyleClass().add("preview-area");

        setTop(toolbar());
        setCenter(center);

        scroll.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> layoutImage());
        scroll.addEventFilter(ScrollEvent.SCROLL, this::onScroll);

        show(startIndex);
    }

    static void open(Window owner, DocumentSession session, List<PageItem> items, int startIndex) {
        if (items.isEmpty()) {
            return;
        }
        Stage stage = new Stage();
        PreviewPane pane = new PreviewPane(session, List.copyOf(items), startIndex, stage::close);
        Scene scene = new Scene(pane, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(PreviewPane.class.getResource("/pdflocal.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, pane::onKey);
        stage.titleProperty().bind(pane.title);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(360);
        stage.show();
    }

    private ToolBar toolbar() {
        previous.setOnAction(event -> show(index - 1));
        next.setOnAction(event -> show(index + 1));
        Button zoomOut = button("-", "preview.zoom-out", () -> changeZoom(false));
        Button zoomIn = button("+", "preview.zoom-in", () -> changeZoom(true));
        Button fit = button(Messages.get("preview.fit"), "preview.fit", () -> {
            zoom.fit();
            layoutImage();
        });
        Button actual = button(Messages.get("preview.actual"), "preview.actual", () -> {
            zoom.actualSize();
            layoutImage();
        });
        zoomLabel.setMinWidth(48);
        zoomLabel.setAlignment(Pos.CENTER);
        return new ToolBar(previous, pageLabel, next, new Separator(), zoomOut, zoomLabel, zoomIn, fit, actual);
    }

    private static Button button(String text, String tooltipKey, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(Messages.get(tooltipKey)));
        button.setOnAction(event -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    private void show(int newIndex) {
        index = Math.max(0, Math.min(items.size() - 1, newIndex));
        PageItem item = items.get(index);
        int token = ++generation;
        image = null;
        imageView.setImage(null);
        failure.setVisible(false);
        progress.setVisible(true);

        pageLabel.setText(Messages.format("preview.page", index + 1, items.size()));
        title.set(Messages.format("preview.title", session.displayName(item), pageLabel.getText()));
        previous.setDisable(index == 0);
        next.setDisable(index == items.size() - 1);

        session.preview(item).whenComplete((loaded, error) -> Platform.runLater(() -> apply(token, loaded, error)));
        prefetch(index + 1);
        prefetch(index - 1);
    }

    private void prefetch(int neighbour) {
        if (neighbour >= 0 && neighbour < items.size()) {
            session.preview(items.get(neighbour)).exceptionally(error -> null);
        }
    }

    private void apply(int token, Image loaded, Throwable error) {
        if (token != generation) {
            return;
        }
        progress.setVisible(false);
        if (error != null) {
            failure.setVisible(true);
            return;
        }
        image = loaded;
        imageView.setImage(loaded);
        layoutImage();
    }

    private void layoutImage() {
        if (image == null || scroll.getViewportBounds() == null) {
            return;
        }
        int rotation = items.get(index).rotation();
        boolean quarterTurn = rotation == 90 || rotation == 270;
        double displayedWidth = quarterTurn ? image.getHeight() : image.getWidth();
        double displayedHeight = quarterTurn ? image.getWidth() : image.getHeight();
        double viewportWidth = scroll.getViewportBounds().getWidth() - VIEWPORT_MARGIN;
        double viewportHeight = scroll.getViewportBounds().getHeight() - VIEWPORT_MARGIN;

        currentScale = zoom.scale(viewportWidth, viewportHeight, displayedWidth, displayedHeight);
        imageView.setFitWidth(image.getWidth() * currentScale);
        imageView.setFitHeight(image.getHeight() * currentScale);
        imageView.setRotate(rotation);
        canvas.setMinSize(
                Math.max(viewportWidth, displayedWidth * currentScale),
                Math.max(viewportHeight, displayedHeight * currentScale));
        zoomLabel.setText(ZoomModel.percent(currentScale) + "%");
    }

    private void changeZoom(boolean in) {
        if (in) {
            zoom.zoomIn(currentScale);
        } else {
            zoom.zoomOut(currentScale);
        }
        layoutImage();
    }

    private void onScroll(ScrollEvent event) {
        if (event.isShortcutDown() && event.getDeltaY() != 0) {
            changeZoom(event.getDeltaY() > 0);
            event.consume();
        }
    }

    private void onKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.ESCAPE) {
            onClose.run();
        } else if (code == KeyCode.LEFT || code == KeyCode.PAGE_UP) {
            show(index - 1);
        } else if (code == KeyCode.RIGHT || code == KeyCode.PAGE_DOWN) {
            show(index + 1);
        } else if (code == KeyCode.PLUS || code == KeyCode.ADD || code == KeyCode.EQUALS) {
            changeZoom(true);
        } else if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) {
            changeZoom(false);
        } else if (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0) {
            zoom.fit();
            layoutImage();
        } else {
            return;
        }
        event.consume();
    }
}
