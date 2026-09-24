package br.com.pdflocal.service;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

public final class ImageToPageService {

    static final double POINTS_PER_MM = 72.0 / 25.4;
    static final double A4_MARGIN = 10 * POINTS_PER_MM;
    static final double MAX_PAGE_DIMENSION = 14_400;

    public record Layout(double pageWidth, double pageHeight, double x, double y, double width, double height) {
    }

    private final FileTypeDetector detector = new FileTypeDetector();
    private final ExifOrientation exifOrientation = new ExifOrientation();

    public PDPage addPage(PDDocument document, Path image, PageSize pageSize) throws PdfLocalException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(pageSize, "pageSize");

        FileType type = detector.detect(image);
        if (!type.isImage()) {
            throw new UnsupportedFileException();
        }

        try {
            PDImageXObject pdImage = createImage(document, image, type);
            int orientation = exifOrientation.read(image);
            int pixelWidth = pdImage.getWidth();
            int pixelHeight = pdImage.getHeight();
            Layout layout = layout(pixelWidth, pixelHeight, orientation, pageSize);

            PDPage page = new PDPage(new PDRectangle((float) layout.pageWidth(), (float) layout.pageHeight()));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdImage, drawingMatrix(layout, pixelWidth, pixelHeight, orientation));
            }
            return page;
        } catch (IOException | RuntimeException e) {
            throw new CorruptFileException(e);
        }
    }

    private static PDImageXObject createImage(PDDocument document, Path image, FileType type) throws IOException {
        if (type == FileType.JPEG || type == FileType.PNG) {
            return PDImageXObject.createFromFileByContent(image.toFile(), document);
        }
        BufferedImage decoded = ImageIO.read(image.toFile());
        if (decoded == null) {
            throw new IOException("Image could not be decoded");
        }
        return LosslessFactory.createFromImage(document, decoded);
    }

    public static Layout layout(int pixelWidth, int pixelHeight, int orientation, PageSize pageSize) {
        boolean swap = ExifOrientation.swapsAxes(orientation);
        double displayedWidth = swap ? pixelHeight : pixelWidth;
        double displayedHeight = swap ? pixelWidth : pixelHeight;

        if (pageSize == PageSize.ORIGINAL) {
            double scale = Math.min(1.0, MAX_PAGE_DIMENSION / Math.max(displayedWidth, displayedHeight));
            double width = displayedWidth * scale;
            double height = displayedHeight * scale;
            return new Layout(width, height, 0, 0, width, height);
        }

        boolean landscape = displayedWidth > displayedHeight;
        double pageWidth = landscape ? PDRectangle.A4.getHeight() : PDRectangle.A4.getWidth();
        double pageHeight = landscape ? PDRectangle.A4.getWidth() : PDRectangle.A4.getHeight();
        double scale = Math.min(
                (pageWidth - 2 * A4_MARGIN) / displayedWidth,
                (pageHeight - 2 * A4_MARGIN) / displayedHeight);
        double width = displayedWidth * scale;
        double height = displayedHeight * scale;
        return new Layout(pageWidth, pageHeight, (pageWidth - width) / 2, (pageHeight - height) / 2, width, height);
    }

    static Matrix drawingMatrix(Layout layout, int pixelWidth, int pixelHeight, int orientation) {
        double[] origin = pagePoint(layout, pixelWidth, pixelHeight, orientation, 0, 0);
        double[] alongX = pagePoint(layout, pixelWidth, pixelHeight, orientation, 1, 0);
        double[] alongY = pagePoint(layout, pixelWidth, pixelHeight, orientation, 0, 1);
        return new Matrix(
                (float) (alongX[0] - origin[0]), (float) (alongX[1] - origin[1]),
                (float) (alongY[0] - origin[0]), (float) (alongY[1] - origin[1]),
                (float) origin[0], (float) origin[1]);
    }

    private static double[] pagePoint(Layout layout, int pixelWidth, int pixelHeight, int orientation,
                                      double u, double v) {
        boolean swap = ExifOrientation.swapsAxes(orientation);
        double displayedWidth = swap ? pixelHeight : pixelWidth;
        double displayedHeight = swap ? pixelWidth : pixelHeight;
        double[] displayed = ExifOrientation.toDisplayed(
                orientation, u * pixelWidth, (1 - v) * pixelHeight, pixelWidth, pixelHeight);
        return new double[] {
                layout.x() + displayed[0] / displayedWidth * layout.width(),
                layout.y() + (1 - displayed[1] / displayedHeight) * layout.height()};
    }
}
