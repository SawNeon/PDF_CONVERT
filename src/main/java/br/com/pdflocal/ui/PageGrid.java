package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageGroup;
import br.com.pdflocal.model.PageGroups;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.ViewMode;
import br.com.pdflocal.ui.GridGeometry.Insertion;
import br.com.pdflocal.util.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

final class PageGrid extends ScrollPane implements PageCard.Actions {

    private static final DataFormat REORDER = new DataFormat("application/x-pdflocal-page-indexes");
    private static final double GAP = 16;
    private static final double INDICATOR_WIDTH = 4;
    private static final double AUTOSCROLL_EDGE = 56;
    private static final double AUTOSCROLL_MAX_SPEED = 20;
    private static final String DRAGGING_STYLE = "dragging";
    private static final int SYNC_LIMIT = 40;
    private static final int BATCH_SIZE = 10;

    private final DocumentSession session;
    private final FlowPane flow = new FlowPane(GAP, GAP);
    private final Pane overlay = new Pane();
    private final Rectangle indicator = new Rectangle(INDICATOR_WIDTH, 0);
    private final Label emptyHint = new Label(Messages.get("grid.empty"));
    private final StackPane content = new StackPane();
    private final PauseTransition thumbnailRefresh = new PauseTransition(Duration.millis(60));
    private final GridSelection selection = new GridSelection();
    private final IntegerProperty selectedCount = new SimpleIntegerProperty();
    private final AnimationTimer autoScroller = new AnimationTimer() {
        @Override
        public void handle(long now) {
            autoScrollStep();
        }
    };
    private final AnimationTimer builder = new AnimationTimer() {
        @Override
        public void handle(long now) {
            buildStep();
        }
    };
    private final Map<UUID, PageCard> cards = new HashMap<>();
    private final Map<UUID, PageCard> built = new HashMap<>();

    private IntConsumer previewHandler;
    private boolean editable = true;
    private ViewMode mode = ViewMode.BY_PAGE;
    private List<PageGroup> target = List.of();
    private List<PageCard> ordered = new ArrayList<>();
    private int cursor;
    private int pushed;
    private double scrollSpeed;
    private double lastSceneX;
    private double lastSceneY;
    private int insertionIndex = -1;

    PageGrid(DocumentSession session) {
        this.session = session;
        getStyleClass().add("page-grid");

        flow.setPadding(new Insets(GAP));
        flow.setAlignment(Pos.TOP_LEFT);
        flow.setMaxHeight(Region.USE_PREF_SIZE);

        indicator.getStyleClass().add("drop-indicator");
        indicator.setArcWidth(INDICATOR_WIDTH);
        indicator.setArcHeight(INDICATOR_WIDTH);
        indicator.setVisible(false);
        overlay.getChildren().add(indicator);
        overlay.setMouseTransparent(true);

        emptyHint.getStyleClass().add("empty-hint");
        emptyHint.setMouseTransparent(true);

        StackPane.setAlignment(flow, Pos.TOP_LEFT);
        StackPane.setAlignment(overlay, Pos.TOP_LEFT);
        content.getChildren().addAll(flow, overlay, emptyHint);
        content.setOnMousePressed(this::onBackgroundPressed);

        setContent(content);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        thumbnailRefresh.setOnFinished(event -> refreshThumbnails());
        vvalueProperty().addListener((observable, oldValue, newValue) -> scheduleThumbnailRefresh());
        viewportBoundsProperty().addListener((observable, oldValue, newValue) -> {
            content.setMinHeight(newValue.getHeight());
            scheduleThumbnailRefresh();
        });
        session.items().addListener((ListChangeListener<PageItem>) change -> rebuild());

        setOnDragOver(this::onDragOver);
        setOnDragDropped(this::onDragDropped);
        setOnDragExited(event -> stopIndicating());

        rebuild();
    }

    IntegerProperty selectedCountProperty() {
        return selectedCount;
    }

    void setViewMode(ViewMode newMode) {
        if (newMode == mode) {
            return;
        }
        mode = newMode;
        builder.stop();
        built.clear();
        cards.values().forEach(PageCard::releaseThumbnail);
        cards.clear();
        selection.clear();
        rebuild();
    }

    void setEditable(boolean value) {
        editable = value;
    }

    void deleteSelection() {
        if (editable && !selection.isEmpty()) {
            session.delete(itemIndices(selection.keys()));
        }
    }

    void selectAll() {
        selection.selectAll(keys());
        applySelection();
    }

    void clearSelection() {
        selection.clear();
        applySelection();
    }

    @Override
    public void rotate(PageCard card, int degrees) {
        if (!editable) {
            return;
        }
        session.rotate(itemIndices(actionTargets(card)), degrees);
    }

    @Override
    public void delete(PageCard card) {
        if (!editable) {
            return;
        }
        session.delete(itemIndices(actionTargets(card)));
    }

    private Set<UUID> actionTargets(PageCard card) {
        return selection.isSelected(card.key()) ? selection.keys() : Set.of(card.key());
    }

    private List<UUID> keys() {
        return target.stream().map(PageGroup::key).toList();
    }

    private List<Integer> itemIndices(Set<UUID> chosen) {
        List<Integer> indices = new ArrayList<>();
        for (PageGroup group : target) {
            if (chosen.contains(group.key())) {
                indices.addAll(group.indices());
            }
        }
        return indices;
    }

    private void rebuild() {
        builder.stop();
        cards.putAll(built);
        built.clear();
        target = PageGroups.of(session.items(), mode);
        dropStaleCards();
        selection.retain(keys());
        ordered = new ArrayList<>(target.size());
        cursor = 0;
        pushed = 0;
        emptyHint.setVisible(target.isEmpty());
        selectedCount.set(selection.size());

        long missing = target.stream().filter(group -> !cards.containsKey(group.key())).count();
        if (missing <= SYNC_LIMIT) {
            while (cursor < target.size()) {
                buildNext();
            }
            flow.getChildren().setAll(ordered);
            finishBuild();
        } else {
            flow.getChildren().clear();
            builder.start();
        }
    }

    private void buildStep() {
        int created = 0;
        while (cursor < target.size() && created < BATCH_SIZE) {
            if (buildNext()) {
                created++;
            }
        }
        flow.getChildren().addAll(ordered.subList(pushed, ordered.size()));
        pushed = ordered.size();
        scheduleThumbnailRefresh();
        if (cursor >= target.size()) {
            builder.stop();
            finishBuild();
        }
    }

    private boolean buildNext() {
        PageGroup group = target.get(cursor);
        PageCard card = built.containsKey(group.key()) ? null : cards.get(group.key());
        boolean created = card == null;
        if (created) {
            card = createCard(group, cursor + 1);
        } else {
            card.update(group, cursor + 1);
        }
        card.setSelected(selection.isSelected(group.key()));
        cursor++;
        built.put(group.key(), card);
        ordered.add(card);
        return created;
    }

    private void finishBuild() {
        cards.clear();
        cards.putAll(built);
        built.clear();
        scheduleThumbnailRefresh();
    }

    private void dropStaleCards() {
        Set<UUID> keep = target.stream().map(PageGroup::key).collect(Collectors.toSet());
        cards.entrySet().removeIf(entry -> {
            if (keep.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().releaseThumbnail();
            return true;
        });
    }

    private PageCard createCard(PageGroup group, int position) {
        PageCard card = new PageCard(group, position, mode, session.displayName(group.first()), this);
        card.setOnMousePressed(event -> onPressed(card, event));
        card.setOnMouseClicked(event -> onClicked(card, event));
        card.setOnDragDetected(event -> startDrag(card, event));
        card.setOnDragDone(event -> finishDrag());
        return card;
    }

    private void applySelection() {
        for (Node node : flow.getChildren()) {
            PageCard card = (PageCard) node;
            card.setSelected(selection.isSelected(card.key()));
        }
        selectedCount.set(selection.size());
    }

    private void onPressed(PageCard card, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        requestFocus();
        boolean ctrl = event.isShortcutDown();
        boolean shift = event.isShiftDown();
        if (!ctrl && !shift && selection.isSelected(card.key())) {
            return;
        }
        selection.click(keys(), flow.getChildren().indexOf(card), ctrl, shift);
        applySelection();
    }

    void setOnPreview(IntConsumer handler) {
        previewHandler = handler;
    }

    private void onClicked(PageCard card, MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && previewHandler != null) {
            previewHandler.accept(card.group().start());
            return;
        }
        boolean plain = !event.isShortcutDown() && !event.isShiftDown();
        if (event.getButton() == MouseButton.PRIMARY && plain
                && selection.size() > 1 && selection.isSelected(card.key())) {
            selection.selectOnly(card.key());
            applySelection();
        }
    }

    private void onBackgroundPressed(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && !isOnCard(event.getTarget())) {
            requestFocus();
            clearSelection();
        }
    }

    private boolean isOnCard(Object target) {
        Node node = target instanceof Node candidate ? candidate : null;
        while (node != null && node != content) {
            if (node instanceof PageCard) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private void scheduleThumbnailRefresh() {
        thumbnailRefresh.playFromStart();
    }

    private void refreshThumbnails() {
        Bounds viewport = getViewportBounds();
        if (viewport == null || viewport.getHeight() <= 0) {
            return;
        }
        if (flow.isNeedsLayout()) {
            scheduleThumbnailRefresh();
            return;
        }

        double top = -viewport.getMinY();
        double bottom = top + viewport.getHeight();
        double near = viewport.getHeight();
        double far = 3 * viewport.getHeight();

        for (Node node : flow.getChildren()) {
            PageCard card = (PageCard) node;
            Bounds bounds = card.getBoundsInParent();
            if (bounds.getMaxY() >= top - near && bounds.getMinY() <= bottom + near) {
                card.requestThumbnail(session::thumbnail);
            } else if (bounds.getMaxY() < top - far || bounds.getMinY() > bottom + far) {
                card.releaseThumbnail();
            }
        }
    }

    private void startDrag(PageCard card, MouseEvent event) {
        if (!editable || flow.getChildren().indexOf(card) < 0) {
            return;
        }
        if (!selection.isSelected(card.key())) {
            selection.selectOnly(card.key());
            applySelection();
        }
        String indices = itemIndices(selection.keys()).stream().map(String::valueOf).collect(Collectors.joining(","));

        Dragboard board = card.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent clipboard = new ClipboardContent();
        clipboard.put(REORDER, indices);
        board.setContent(clipboard);

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage ghost = card.snapshot(parameters, null);
        board.setDragView(ghost, event.getX(), event.getY());

        flow.getChildren().stream()
                .map(PageCard.class::cast)
                .filter(other -> selection.isSelected(other.key()))
                .forEach(other -> other.getStyleClass().add(DRAGGING_STYLE));
        autoScroller.start();
        event.consume();
    }

    private void finishDrag() {
        flow.getChildren().forEach(node -> node.getStyleClass().remove(DRAGGING_STYLE));
        stopIndicating();
        autoScroller.stop();
    }

    private void onDragOver(DragEvent event) {
        if (!event.getDragboard().hasContent(REORDER)) {
            return;
        }
        event.acceptTransferModes(TransferMode.MOVE);
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();
        updateIndicator();
        updateAutoScroll(event.getY());
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard board = event.getDragboard();
        if (!board.hasContent(REORDER)) {
            return;
        }
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();
        updateIndicator();

        List<Integer> indices = Arrays.stream(((String) board.getContent(REORDER)).split(","))
                .map(Integer::parseInt)
                .toList();
        int groupIndex = insertionIndex;
        stopIndicating();

        boolean valid = groupIndex >= 0 && indices.stream().allMatch(index -> index < session.items().size());
        if (valid) {
            int itemInsertion = groupIndex < target.size() ? target.get(groupIndex).start() : session.items().size();
            session.move(indices, itemInsertion);
        }
        event.setDropCompleted(valid);
        event.consume();
    }

    private void updateIndicator() {
        List<Rectangle2D> cells = new ArrayList<>(flow.getChildren().size());
        for (Node node : flow.getChildren()) {
            Bounds bounds = node.getBoundsInParent();
            cells.add(new Rectangle2D(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
        }
        Point2D point = flow.sceneToLocal(lastSceneX, lastSceneY);
        if (point == null || cells.isEmpty()) {
            return;
        }

        Insertion insertion = GridGeometry.insertionAt(cells, point.getX(), point.getY(), GAP);
        insertionIndex = insertion.index();
        indicator.setHeight(insertion.height());
        indicator.setLayoutX(insertion.x() - INDICATOR_WIDTH / 2);
        indicator.setLayoutY(insertion.top());
        indicator.setVisible(true);
    }

    private void stopIndicating() {
        indicator.setVisible(false);
        insertionIndex = -1;
        scrollSpeed = 0;
    }

    private void updateAutoScroll(double y) {
        double height = getHeight();
        if (y < AUTOSCROLL_EDGE) {
            scrollSpeed = -AUTOSCROLL_MAX_SPEED * (AUTOSCROLL_EDGE - Math.max(y, 0)) / AUTOSCROLL_EDGE;
        } else if (y > height - AUTOSCROLL_EDGE) {
            scrollSpeed = AUTOSCROLL_MAX_SPEED * (Math.min(y, height) - (height - AUTOSCROLL_EDGE)) / AUTOSCROLL_EDGE;
        } else {
            scrollSpeed = 0;
        }
    }

    private void autoScrollStep() {
        if (scrollSpeed == 0) {
            return;
        }
        double range = content.getHeight() - getViewportBounds().getHeight();
        if (range <= 0) {
            return;
        }
        setVvalue(Math.max(0, Math.min(1, getVvalue() + scrollSpeed / range)));
        updateIndicator();
    }
}
