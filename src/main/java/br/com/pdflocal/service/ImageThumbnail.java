package br.com.pdflocal.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class ImageThumbnail {

    private ImageThumbnail() {
    }

    static BufferedImage create(Path file, int maxSide, int orientation) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            throw new IOException("Image could not be decoded");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = downscale(source, targetWidth, targetHeight);
        return orient(scaled, orientation);
    }

    private static BufferedImage downscale(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight || current == source) {
            current = draw(current, targetWidth, targetHeight);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == ExifOrientation.NORMAL) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = ExifOrientation.swapsAxes(orientation);
        BufferedImage target = new BufferedImage(
                swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);

        double[] origin = ExifOrientation.toDisplayed(orientation, 0, 0, width, height);
        double[] alongX = ExifOrientation.toDisplayed(orientation, 1, 0, width, height);
        double[] alongY = ExifOrientation.toDisplayed(orientation, 0, 1, width, height);
        AffineTransform transform = new AffineTransform(
                alongX[0] - origin[0], alongX[1] - origin[1],
                alongY[0] - origin[0], alongY[1] - origin[1],
                origin[0], origin[1]);

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
