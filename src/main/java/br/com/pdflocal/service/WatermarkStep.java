package br.com.pdflocal.service;

import br.com.pdflocal.model.WatermarkOptions;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

public final class WatermarkStep implements OutputStep {

    private static final double DIAGONAL_ANGLE = Math.PI / 4;
    private static final double TEXT_SPAN = 0.7;
    private static final double IMAGE_SPAN = 0.5;
    private static final double MIN_TEXT_SIZE = 12;
    private static final double MAX_TEXT_SIZE = 220;
    private static final double BASELINE_SHIFT = 0.35;
    private static final float GRAY = 0.45f;

    private final WatermarkOptions options;

    public WatermarkStep(WatermarkOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        PDType1Font font = StandardText.bold();
        PDImageXObject image = options.isText() ? null
                : PDImageXObject.createFromFileByContent(options.imagePath().toFile(), document);
        PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
        transparency.setNonStrokingAlphaConstant((float) options.opacity());
        transparency.setStrokingAlphaConstant((float) options.opacity());

        int pages = document.getNumberOfPages();
        for (int index = 0; index < pages; index++) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(index + 1, pages);
            PDPage page = document.getPage(index);
            EditableResources.privateCopy(page);
            try (PDPageContentStream content = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                content.setGraphicsStateParameters(transparency);
                if (image == null) {
                    drawText(content, page, font);
                } else {
                    drawImage(content, page, image);
                }
            }
        }
    }

    private void drawText(PDPageContentStream content, PDPage page, PDType1Font font) throws IOException {
        PDRectangle box = PageGeometry.visibleBox(page);
        int rotation = PageGeometry.rotation(page);
        double displayWidth = PageGeometry.displayWidth(box, rotation);
        double displayHeight = PageGeometry.displayHeight(box, rotation);
        double angle = options.diagonal() ? DIAGONAL_ANGLE : 0;
        double span = options.diagonal() ? Math.hypot(displayWidth, displayHeight) : displayWidth;

        double unitWidth = StandardText.width(font, options.text(), 1.0);
        double size = Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, TEXT_SPAN * span / unitWidth));
        double width = unitWidth * size;

        double centerX = displayWidth / 2;
        double centerY = displayHeight / 2;
        double startX = centerX - Math.cos(angle) * width / 2 + Math.sin(angle) * size * BASELINE_SHIFT;
        double startY = centerY - Math.sin(angle) * width / 2 - Math.cos(angle) * size * BASELINE_SHIFT;
        PageGeometry.Placement placement = PageGeometry.place(box, rotation, startX, startY);

        content.beginText();
        content.setFont(font, (float) size);
        content.setNonStrokingColor(GRAY);
        content.setTextMatrix(Matrix.getRotateInstance(placement.angle() + angle, (float) placement.x(),
                (float) placement.y()));
        content.showText(options.text());
        content.endText();
    }

    private void drawImage(PDPageContentStream content, PDPage page, PDImageXObject image) throws IOException {
        PDRectangle box = PageGeometry.visibleBox(page);
        int rotation = PageGeometry.rotation(page);
        double displayWidth = PageGeometry.displayWidth(box, rotation);
        double displayHeight = PageGeometry.displayHeight(box, rotation);
        double scale = Math.min(IMAGE_SPAN * displayWidth / image.getWidth(),
                IMAGE_SPAN * displayHeight / image.getHeight());
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;
        double angle = options.diagonal() ? DIAGONAL_ANGLE : 0;

        PageGeometry.Placement center = PageGeometry.place(box, rotation, displayWidth / 2, displayHeight / 2);
        double total = center.angle() + angle;
        double cos = Math.cos(total);
        double sin = Math.sin(total);
        float e = (float) (center.x() - width / 2 * cos + height / 2 * sin);
        float f = (float) (center.y() - width / 2 * sin - height / 2 * cos);
        content.drawImage(image, new Matrix((float) (width * cos), (float) (width * sin),
                (float) (-height * sin), (float) (height * cos), e, f));
    }
}
