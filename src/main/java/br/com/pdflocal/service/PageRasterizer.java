package br.com.pdflocal.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

final class PageRasterizer {

    static final int DEFAULT_DPI = 200;
    static final long MAX_PIXELS = 25_000_000L;
    private static final double POINTS_PER_INCH = 72.0;
    private static final float JPEG_QUALITY = 0.9f;

    record Rendered(BufferedImage image, double pointsPerPixel) {
    }

    private final PDDocument document;
    private final PDFRenderer renderer;

    PageRasterizer(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
    }

    static double effectiveDpi(double widthPoints, double heightPoints, int requestedDpi) {
        double areaInches = widthPoints / POINTS_PER_INCH * heightPoints / POINTS_PER_INCH;
        if (areaInches <= 0) {
            return requestedDpi;
        }
        return Math.min(requestedDpi, Math.sqrt(MAX_PIXELS / areaInches));
    }

    Rendered render(int pageIndex, int dpi) throws IOException {
        PDPage page = document.getPage(pageIndex);
        PDRectangle box = PageGeometry.visibleBox(page);
        int rotation = PageGeometry.rotation(page);
        double used = effectiveDpi(PageGeometry.displayWidth(box, rotation), PageGeometry.displayHeight(box, rotation),
                dpi);
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, (float) used, ImageType.RGB);
        return new Rendered(image, POINTS_PER_INCH / used);
    }

    void replace(int pageIndex, BufferedImage image, double pointsPerPixel) throws IOException {
        PDPage old = document.getPage(pageIndex);
        PDPage created = new PDPage(new PDRectangle((float) (image.getWidth() * pointsPerPixel),
                (float) (image.getHeight() * pointsPerPixel)));
        PDImageXObject pdImage = JPEGFactory.createFromImage(document, image, JPEG_QUALITY);
        try (PDPageContentStream content = new PDPageContentStream(document, created)) {
            content.drawImage(pdImage, 0, 0, created.getMediaBox().getWidth(), created.getMediaBox().getHeight());
        }
        document.getPages().insertBefore(created, old);
        document.getPages().remove(old);
    }
}
