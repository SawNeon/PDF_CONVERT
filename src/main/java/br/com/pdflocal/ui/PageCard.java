package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageGroup;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.ViewMode;
import br.com.pdflocal.util.Messages;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class PageCard extends VBox {

    interface Actions {
        void rotate(PageCard card, int degrees);

        void delete(PageCard card);
    }

    static final double THUMBNAIL_WIDTH = 160;
    static final double THUMBNAIL_HEIGHT = 210;

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass STACKED = PseudoClass.getPseudoClass("stacked");

    private enum State {
        EMPTY, LOADING, LOADED, FAILED
    }

    private final ViewMode mode;
    private final ImageView imageView = new ImageView();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label failure = new Label(Messages.get("card.thumbnail.failed"));
    private final Label badge = new Label();
    private final Label origin = new Label();
    private final StackPane thumbnailBox;
    private PageGroup group;
    private Image image;
    private State state = State.EMPTY;
    private int generation;

    PageCard(PageGroup group, int position, ViewMode mode, String fileName, Actions actions) {
        this.group = group;
        this.mode = mode;
        getStyleClass().add("page-card");
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Region.USE_PREF_SIZE);

        imageView.setFitWidth(THUMBNAIL_WIDTH);
        imageView.setFitHeight(THUMBNAIL_HEIGHT);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        progress.setMaxSize(28, 28);
        progress.setVisible(false);
        failure.getStyleClass().add("page-subcaption");
        failure.setVisible(false);
        badge.getStyleClass().add("page-badge");
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(4));

        thumbnailBox = new StackPane(imageView, progress, failure, badge, actionBar(actions));
        thumbnailBox.getStyleClass().add("thumbnail-box");
        thumbnailBox.setMinSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        thumbnailBox.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        thumbnailBox.setMaxSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);

        Label name = new Label(fileName);
        name.getStyleClass().add("page-caption");
        name.setMaxWidth(THUMBNAIL_WIDTH);
        name.setTooltip(new Tooltip(fileName));
        origin.getStyleClass().add("page-subcaption");

        getChildren().addAll(thumbnailBox, name, origin);
        update(group, position);
    }

    UUID key() {
        return group.key();
    }

    PageGroup group() {
        return group;
    }

    void update(PageGroup group, int position) {
        this.group = group;
        badge.setText(String.valueOf(position));
        origin.setText(originText());
        thumbnailBox.pseudoClassStateChanged(STACKED, group.items().size() > 1);
        layoutImage();
    }

    void setSelected(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }

    void requestThumbnail(Function<PageItem, CompletableFuture<Image>> loader) {
        if (state != State.EMPTY) {
            return;
        }
        state = State.LOADING;
        progress.setVisible(true);
        int token = ++generation;
        loader.apply(group.first()).whenComplete((loaded, error) -> Platform.runLater(() -> apply(token, loaded, error)));
    }

    void releaseThumbnail() {
        if (state == State.LOADED || state == State.LOADING) {
            generation++;
            image = null;
            imageView.setImage(null);
            progress.setVisible(false);
            state = State.EMPTY;
        }
    }

    private HBox actionBar(Actions actions) {
        Button rotateLeft = actionButton("icon-rotate-left", "card.action.rotate-left", () -> actions.rotate(this, -90));
        Button rotateRight = actionButton("icon-rotate-right", "card.action.rotate-right", () -> actions.rotate(this, 90));
        Button delete = actionButton("icon-delete", "card.action.delete", () -> actions.delete(this));
        delete.getStyleClass().add("danger");

        HBox bar = new HBox(4, rotateLeft, rotateRight, delete);
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bar.visibleProperty().bind(hoverProperty());
        bar.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);
        bar.addEventHandler(MouseEvent.MOUSE_CLICKED, Event::consume);
        bar.addEventHandler(MouseEvent.DRAG_DETECTED, Event::consume);
        StackPane.setAlignment(bar, Pos.TOP_RIGHT);
        StackPane.setMargin(bar, new Insets(4));
        return bar;
    }

    private static Button actionButton(String iconStyle, String tooltipKey, Runnable action) {
        Region icon = new Region();
        icon.getStyleClass().addAll("icon", iconStyle);
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("card-action");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(Messages.get(tooltipKey)));
        button.setOnAction(event -> action.run());
        return button;
    }

    private String originText() {
        PageItem first = group.first();
        if (mode == ViewMode.BY_FILE && !first.isImage()) {
            return Messages.format("card.pages", group.items().size());
        }
        return first.isImage() ? Messages.get("card.source.image") : Messages.format("card.source.page", first.pageIndex() + 1);
    }

    private void layoutImage() {
        if (image == null) {
            return;
        }
        int rotation = group.first().rotation();
        boolean quarterTurn = rotation == 90 || rotation == 270;
        double width = image.getWidth();
        double height = image.getHeight();
        double displayedWidth = quarterTurn ? height : width;
        double displayedHeight = quarterTurn ? width : height;
        double scale = Math.min(THUMBNAIL_WIDTH / displayedWidth, THUMBNAIL_HEIGHT / displayedHeight);
        imageView.setFitWidth(width * scale);
        imageView.setFitHeight(height * scale);
        imageView.setRotate(rotation);
    }

    private void apply(int token, Image loaded, Throwable error) {
        if (token != generation) {
            return;
        }
        progress.setVisible(false);
        if (error == null) {
            image = loaded;
            imageView.setImage(loaded);
            layoutImage();
            state = State.LOADED;
        } else if (isCancellation(error)) {
            state = State.EMPTY;
        } else {
            state = State.FAILED;
            failure.setVisible(true);
        }
    }

    private static boolean isCancellation(Throwable error) {
        return error instanceof CancellationException || error.getCause() instanceof CancellationException;
    }
}
