package br.com.pdflocal.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

final class ImageRotation {

    private ImageRotation() {
    }

    static Image rotate(Image source, int rotation) {
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
