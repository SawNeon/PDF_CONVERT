package br.com.pdflocal.service;

import br.com.pdflocal.model.RedactionArea;
import br.com.pdflocal.util.SaveCancelledException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class RedactStep implements OutputStep {

    private final Map<Integer, List<RedactionArea>> areasByPage;

    public RedactStep(Map<Integer, List<RedactionArea>> areasByPage) {
        Objects.requireNonNull(areasByPage, "areasByPage");
        Map<Integer, List<RedactionArea>> copy = new HashMap<>();
        areasByPage.forEach((page, areas) -> {
            if (page < 0) {
                throw new IllegalArgumentException("Invalid page index: " + page);
            }
            if (areas != null && !areas.isEmpty()) {
                copy.put(page, List.copyOf(areas));
            }
        });
        this.areasByPage = new TreeMap<>(copy);
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        int pages = document.getNumberOfPages();
        for (int page : areasByPage.keySet()) {
            if (page >= pages) {
                throw new IllegalStateException("A redaction refers to a page that does not exist");
            }
        }
        PageRasterizer rasterizer = new PageRasterizer(document);
        int done = 0;
        for (Map.Entry<Integer, List<RedactionArea>> entry : areasByPage.entrySet()) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(done++, areasByPage.size());
            PageRasterizer.Rendered rendered = rasterizer.render(entry.getKey(), PageRasterizer.DEFAULT_DPI);
            paint(rendered.image(), entry.getValue());
            rasterizer.replace(entry.getKey(), rendered.image(), rendered.pointsPerPixel());
        }
        listener.onTransform(areasByPage.size(), areasByPage.size());
    }

    private static void paint(BufferedImage image, List<RedactionArea> areas) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (RedactionArea area : areas) {
                int x0 = (int) Math.floor(area.left() * image.getWidth());
                int y0 = (int) Math.floor(area.top() * image.getHeight());
                int x1 = (int) Math.ceil(area.right() * image.getWidth());
                int y1 = (int) Math.ceil(area.bottom() * image.getHeight());
                graphics.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
            }
        } finally {
            graphics.dispose();
        }
    }
}
