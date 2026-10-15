package br.com.pdflocal.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class ImageScaler {

    private ImageScaler() {
    }

    static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight, boolean gray) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2, gray);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight || current == source) {
            current = draw(current, targetWidth, targetHeight, gray);
        }
        return current;
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, boolean gray) {
        int type = gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
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
}
