package br.com.pdflocal.model;

import java.nio.file.Path;
import java.util.UUID;

public record PageItem(UUID sourceId, int pageIndex, Path imagePath, int rotation) {

    public PageItem {
        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
            throw new IllegalArgumentException("Invalid rotation: " + rotation);
        }
        boolean pdfPage = sourceId != null && imagePath == null && pageIndex >= 0;
        boolean image = sourceId == null && imagePath != null && pageIndex == 0;
        if (!pdfPage && !image) {
            throw new IllegalArgumentException("A PageItem is either a PDF page or an image");
        }
    }

    public static PageItem pdfPage(UUID sourceId, int pageIndex) {
        return new PageItem(sourceId, pageIndex, null, 0);
    }

    public static PageItem image(Path imagePath) {
        return new PageItem(null, 0, imagePath, 0);
    }

    public boolean isImage() {
        return imagePath != null;
    }

    public PageItem rotatedBy(int degrees) {
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90: " + degrees);
        }
        return new PageItem(sourceId, pageIndex, imagePath, Math.floorMod(rotation + degrees, 360));
    }
}
