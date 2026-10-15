package br.com.pdflocal.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

final class AreasPickerPane extends Pane {

    private final double maxWidth;
    private final double maxHeight;
    private final ImageView view = new ImageView();
    private final Rectangle dragging = new Rectangle();
    private final List<NormalizedRegion> areas = new ArrayList<>();
    private final List<Rectangle> shapes = new ArrayList<>();
    private double anchorX;
    private double anchorY;
    private Runnable onChange = () -> { };

    AreasPickerPane(double maxWidth, double maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        view.setPreserveRatio(true);
        view.setFitWidth(maxWidth);
        view.setFitHeight(maxHeight);
        styleAsShape(dragging, 0.35);
        dragging.setVisible(false);
        getChildren().addAll(view, dragging);
        getStyleClass().add("region-picker");
        fitTo(maxWidth, maxHeight);
        view.setOnMousePressed(this::onPressed);
        view.setOnMouseDragged(this::onDragged);
        view.setOnMouseReleased(this::onReleased);
    }

    void setOnChange(Runnable listener) {
        this.onChange = listener;
    }

    void show(Image image, int rotation, List<NormalizedRegion> existing) {
        Image shown = ImageRotation.rotate(image, rotation);
        view.setImage(shown);
        if (shown != null && shown.getWidth() > 0 && shown.getHeight() > 0) {
            double scale = Math.min(maxWidth / shown.getWidth(), maxHeight / shown.getHeight());
            fitTo(Math.round(shown.getWidth() * scale), Math.round(shown.getHeight() * scale));
        }
        areas.clear();
        areas.addAll(existing);
        redraw();
    }

    List<NormalizedRegion> areas() {
        return List.copyOf(areas);
    }

    void undoLast() {
        if (!areas.isEmpty()) {
            areas.remove(areas.size() - 1);
            redraw();
            onChange.run();
        }
    }

    void clear() {
        if (!areas.isEmpty()) {
            areas.clear();
            redraw();
            onChange.run();
        }
    }

    private void onPressed(MouseEvent event) {
        anchorX = event.getX();
        anchorY = event.getY();
        dragging.setVisible(false);
    }

    private void onDragged(MouseEvent event) {
        double width = view.getBoundsInLocal().getWidth();
        double height = view.getBoundsInLocal().getHeight();
        double x0 = Math.max(0, Math.min(width, Math.min(anchorX, event.getX())));
        double x1 = Math.max(0, Math.min(width, Math.max(anchorX, event.getX())));
        double y0 = Math.max(0, Math.min(height, Math.min(anchorY, event.getY())));
        double y1 = Math.max(0, Math.min(height, Math.max(anchorY, event.getY())));
        dragging.setX(x0);
        dragging.setY(y0);
        dragging.setWidth(x1 - x0);
        dragging.setHeight(y1 - y0);
        dragging.setVisible(true);
    }

    private void onReleased(MouseEvent event) {
        dragging.setVisible(false);
        NormalizedRegion region = NormalizedRegion.fromDrag(anchorX, anchorY, event.getX(), event.getY(),
                view.getBoundsInLocal().getWidth(), view.getBoundsInLocal().getHeight());
        if (region != null) {
            areas.add(region);
            redraw();
            onChange.run();
        }
    }

    private void redraw() {
        getChildren().removeAll(shapes);
        shapes.clear();
        double width = view.getBoundsInLocal().getWidth();
        double height = view.getBoundsInLocal().getHeight();
        for (NormalizedRegion area : areas) {
            Rectangle shape = new Rectangle(area.left() * width, area.top() * height,
                    (area.right() - area.left()) * width, (area.bottom() - area.top()) * height);
            styleAsShape(shape, 0.7);
            shape.setMouseTransparent(true);
            shapes.add(shape);
        }
        getChildren().addAll(shapes);
        dragging.toFront();
    }

    private void fitTo(double width, double height) {
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
    }

    private static void styleAsShape(Rectangle shape, double opacity) {
        shape.setMouseTransparent(true);
        shape.setFill(Color.rgb(0, 0, 0, opacity));
        shape.setStroke(Color.rgb(200, 30, 30));
        shape.setStrokeWidth(1.5);
    }
}
