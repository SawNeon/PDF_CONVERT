package br.com.pdflocal.service;

import br.com.pdflocal.model.ImageExportOptions;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class PageExporter {

    private static final float JPEG_QUALITY = 0.9f;
    private static final int MIN_NUMBER_DIGITS = 3;

    private static final Logger LOG = Logger.getLogger(PageExporter.class.getName());

    private final DocumentComposer composer;

    public PageExporter(DocumentComposer composer) {
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    public SaveResult export(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize,
                             ImageExportOptions options, Path output, SaveListener listener)
            throws PdfLocalException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(listener, "listener");
        DocumentComposer.Produced<Void> produced = composer.produce(items, registry, imagePageSize, output, listener,
                (document, temporary) -> {
                    write(document, temporary, options, listener);
                    return null;
                });
        LOG.log(Level.INFO, "Images exported from {0} pages", items.size());
        return new SaveResult(items.size(), produced.bytes(), CompressionStats.EMPTY);
    }

    private void write(PDDocument document, Path temporary, ImageExportOptions options, SaveListener listener)
            throws IOException, SaveCancelledException {
        PageRasterizer rasterizer = new PageRasterizer(document);
        int pages = document.getNumberOfPages();
        if (pages == 1) {
            listener.onTransform(0, pages);
            try (OutputStream out = Files.newOutputStream(temporary)) {
                out.write(encode(rasterizer.render(0, options.dpi()).image(), options.format()));
            }
            listener.onTransform(1, pages);
            return;
        }
        int digits = Math.max(MIN_NUMBER_DIGITS, String.valueOf(pages).length());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for (int index = 0; index < pages; index++) {
                if (listener.isCancelled()) {
                    throw new SaveCancelledException();
                }
                listener.onTransform(index, pages);
                byte[] data = encode(rasterizer.render(index, options.dpi()).image(), options.format());
                zip.putNextEntry(new ZipEntry(String.format("pagina-%0" + digits + "d.%s", index + 1,
                        options.format().extension())));
                zip.write(data);
                zip.closeEntry();
            }
            listener.onTransform(pages, pages);
        }
    }

    private static byte[] encode(BufferedImage image, ImageExportOptions.Format format) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (format == ImageExportOptions.Format.PNG) {
            if (!ImageIO.write(image, "png", buffer)) {
                throw new IOException("No PNG writer available");
            }
            return buffer.toByteArray();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(buffer)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }
}
