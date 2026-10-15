package br.com.pdflocal.service;

import br.com.pdflocal.model.CropOptions;
import br.com.pdflocal.util.SaveCancelledException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public final class CropStep implements OutputStep {

    private final CropOptions options;

    public CropStep(CropOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        int pages = document.getNumberOfPages();
        PageRasterizer rasterizer = options.mode() == CropOptions.Mode.REMOVE_OUTSIDE
                ? new PageRasterizer(document) : null;
        for (int index = 0; index < pages; index++) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(index + 1, pages);
            if (rasterizer == null) {
                hide(document.getPage(index));
            } else {
                remove(rasterizer, index);
            }
        }
    }

    private void hide(PDPage page) {
        PDRectangle box = PageGeometry.visibleBox(page);
        int rotation = PageGeometry.rotation(page);
        double width = PageGeometry.displayWidth(box, rotation);
        double height = PageGeometry.displayHeight(box, rotation);
        PDRectangle area = PageGeometry.toUserSpace(box, rotation,
                options.left() * width, (1 - options.bottom()) * height,
                options.right() * width, (1 - options.top()) * height);
        page.setCropBox(area);
    }

    private void remove(PageRasterizer rasterizer, int index) throws IOException {
        PageRasterizer.Rendered rendered = rasterizer.render(index, PageRasterizer.DEFAULT_DPI);
        BufferedImage full = rendered.image();
        int x = (int) Math.round(options.left() * full.getWidth());
        int y = (int) Math.round(options.top() * full.getHeight());
        int width = Math.max(1, Math.min(full.getWidth() - x, (int) Math.round((options.right() - options.left()) * full.getWidth())));
        int height = Math.max(1, Math.min(full.getHeight() - y, (int) Math.round((options.bottom() - options.top()) * full.getHeight())));
        BufferedImage region = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        region.getGraphics().drawImage(full.getSubimage(x, y, width, height), 0, 0, null);
        rasterizer.replace(index, region, rendered.pointsPerPixel());
    }
}
