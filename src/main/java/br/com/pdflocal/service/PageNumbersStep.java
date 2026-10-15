package br.com.pdflocal.service;

import br.com.pdflocal.model.PageNumberOptions;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

public final class PageNumbersStep implements OutputStep {

    static final double MARGIN = 28;
    private static final float GRAY = 0.2f;

    private final PageNumberOptions options;

    public PageNumbersStep(PageNumberOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        int pages = document.getNumberOfPages();
        int lastNumber = options.startNumber() + pages - 1;
        PDType1Font font = StandardText.regular();
        for (int index = 0; index < pages; index++) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(index + 1, pages);
            if (index == 0 && options.skipFirstPage()) {
                continue;
            }
            stamp(document, document.getPage(index), font, options.render(options.startNumber() + index, lastNumber));
        }
    }

    private void stamp(PDDocument document, PDPage page, PDType1Font font, String text) throws IOException {
        PDRectangle box = PageGeometry.visibleBox(page);
        int rotation = PageGeometry.rotation(page);
        double displayWidth = PageGeometry.displayWidth(box, rotation);
        double displayHeight = PageGeometry.displayHeight(box, rotation);
        double size = options.fontSize();
        double textWidth = StandardText.width(font, text, size);

        double x;
        if (options.position().isLeft()) {
            x = MARGIN;
        } else if (options.position().isRight()) {
            x = displayWidth - MARGIN - textWidth;
        } else {
            x = (displayWidth - textWidth) / 2;
        }
        double y = options.position().isTop() ? displayHeight - MARGIN - size : MARGIN;
        PageGeometry.Placement placement = PageGeometry.place(box, rotation, Math.max(0, x), y);

        EditableResources.privateCopy(page);
        try (PDPageContentStream content = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            content.beginText();
            content.setFont(font, (float) size);
            content.setNonStrokingColor(GRAY);
            content.setTextMatrix(Matrix.getRotateInstance(placement.angle(), (float) placement.x(),
                    (float) placement.y()));
            content.showText(text);
            content.endText();
        }
    }
}
