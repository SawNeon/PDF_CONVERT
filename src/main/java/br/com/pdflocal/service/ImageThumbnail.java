package br.com.pdflocal.service;

import java.awt.Graphics2D;
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

        BufferedImage scaled = ImageScaler.scale(source, targetWidth, targetHeight, false);
        return orient(scaled, orientation);
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
