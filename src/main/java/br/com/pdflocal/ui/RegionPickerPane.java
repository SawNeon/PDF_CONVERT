package br.com.pdflocal.ui;

import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

final class RegionPickerPane extends Pane {

    private final double maxWidth;
    private final double maxHeight;
    private final ImageView view = new ImageView();
    private final Rectangle selection = new Rectangle();
    private double anchorX;
    private double anchorY;
    private NormalizedRegion region;
    private Consumer<Optional<NormalizedRegion>> onChange = value -> { };

    RegionPickerPane(double maxWidth, double maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        view.setPreserveRatio(true);
        view.setFitWidth(maxWidth);
        view.setFitHeight(maxHeight);
        selection.setVisible(false);
        selection.setMouseTransparent(true);
        selection.getStyleClass().add("region-selection");
        selection.setFill(Color.rgb(0, 153, 102, 0.22));
        selection.setStroke(Color.rgb(0, 153, 102));
        selection.setStrokeWidth(2);
        getChildren().addAll(view, selection);
        getStyleClass().add("region-picker");
        fitTo(maxWidth, maxHeight);
        view.setOnMousePressed(this::onPressed);
        view.setOnMouseDragged(this::onDragged);
        view.setOnMouseReleased(this::onReleased);
    }

    void setOnChange(Consumer<Optional<NormalizedRegion>> listener) {
        this.onChange = listener;
    }

    void show(Image image, int rotation) {
        Image shown = rotated(image, rotation);
        view.setImage(shown);
        if (shown != null && shown.getWidth() > 0 && shown.getHeight() > 0) {
            double scale = Math.min(maxWidth / shown.getWidth(), maxHeight / shown.getHeight());
            fitTo(Math.round(shown.getWidth() * scale), Math.round(shown.getHeight() * scale));
        }
        clear();
    }

    private void fitTo(double width, double height) {
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
    }

    Optional<NormalizedRegion> region() {
        return Optional.ofNullable(region);
    }

    void clear() {
        region = null;
        selection.setVisible(false);
        onChange.accept(Optional.empty());
    }

    private void onPressed(MouseEvent event) {
        anchorX = event.getX();
        anchorY = event.getY();
        selection.setVisible(false);
        region = null;
        onChange.accept(Optional.empty());
    }

    private void onDragged(MouseEvent event) {
        double width = view.getBoundsInLocal().getWidth();
        double height = view.getBoundsInLocal().getHeight();
        double x0 = Math.max(0, Math.min(width, Math.min(anchorX, event.getX())));
        double x1 = Math.max(0, Math.min(width, Math.max(anchorX, event.getX())));
        double y0 = Math.max(0, Math.min(height, Math.min(anchorY, event.getY())));
        double y1 = Math.max(0, Math.min(height, Math.max(anchorY, event.getY())));
        selection.setX(x0);
        selection.setY(y0);
        selection.setWidth(x1 - x0);
        selection.setHeight(y1 - y0);
        selection.setVisible(true);
    }

    private void onReleased(MouseEvent event) {
        double width = view.getBoundsInLocal().getWidth();
        double height = view.getBoundsInLocal().getHeight();
        region = NormalizedRegion.fromDrag(anchorX, anchorY, event.getX(), event.getY(), width, height);
        selection.setVisible(region != null);
        onChange.accept(Optional.ofNullable(region));
    }

    private static Image rotated(Image source, int rotation) {
        if (source == null || Math.floorMod(rotation, 360) == 0) {
            return source;
        }
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        if (reader == null || width <= 0 || height <= 0) {
            return source;
        }
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = reader.getArgb(x, y);
            }
        }
        PixelRotation.Rotated result = PixelRotation.rotate(pixels, width, height, rotation);
        WritableImage image = new WritableImage(result.width(), result.height());
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < result.height(); y++) {
            for (int x = 0; x < result.width(); x++) {
                writer.setArgb(x, y, result.pixels()[y * result.width() + x]);
            }
        }
        return image;
    }
}
