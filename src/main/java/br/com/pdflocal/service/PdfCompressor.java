package br.com.pdflocal.service;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.util.SaveCancelledException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDCalGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class PdfCompressor {

    static final double MIN_GAIN_RATIO = 0.95;
    static final double RESAMPLE_THRESHOLD = 1.15;
    static final int MIN_SIDE_PIXELS = 16;
    private static final int MAX_SUBSAMPLING = 8;
    private static final int CMYK_COMPONENTS = 4;

    private static final Logger LOG = Logger.getLogger(PdfCompressor.class.getName());

    private record Entry(PDPage page, COSName name, PDImageXObject image) {
    }

    private record Outcome(PDImageXObject replacement, long before, long after) {
    }

    public CompressionStats compress(PDDocument document, CompressionLevel level, SaveListener listener)
            throws IOException, SaveCancelledException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(listener, "listener");
        if (!level.isEnabled()) {
            return CompressionStats.EMPTY;
        }

        Map<COSStream, Double> resolutions = new ImageResolutionFinder().measure(document, listener);
        List<Entry> entries = collect(document);
        Map<COSStream, Outcome> processed = new IdentityHashMap<>();
        Map<PDPage, COSDictionary> editableXObjects = new IdentityHashMap<>();
        int replaced = 0;
        long before = 0;
        long after = 0;
        int done = 0;

        for (Entry entry : entries) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            COSStream stream = streamOf(entry.image());
            Outcome outcome = stream == null ? null : processed.get(stream);
            boolean firstTime = outcome == null;
            if (firstTime) {
                outcome = process(document, entry, level, resolutions);
                if (stream != null) {
                    processed.put(stream, outcome);
                }
            }
            if (outcome.replacement() != null) {
                editableXObjects.computeIfAbsent(entry.page(), PdfCompressor::makeEditable)
                        .setItem(entry.name(), outcome.replacement().getCOSObject());
                if (firstTime) {
                    replaced++;
                }
            }
            if (firstTime) {
                before += outcome.before();
                after += outcome.after();
            }
            listener.onCompress(++done, entries.size());
        }

        LOG.log(Level.INFO, "Compression: {0} images, {1} replaced", new Object[] {entries.size(), replaced});
        return new CompressionStats(entries.size(), replaced, before, after);
    }

    private static List<Entry> collect(PDDocument document) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(name);
                if (xObject instanceof PDImageXObject image) {
                    entries.add(new Entry(page, name, image));
                }
            }
        }
        return entries;
    }

    private static COSDictionary makeEditable(PDPage page) {
        COSDictionary resources = new COSDictionary(page.getResources().getCOSObject());
        COSDictionary xObjects = new COSDictionary(resources.getCOSDictionary(COSName.XOBJECT));
        resources.setItem(COSName.XOBJECT, xObjects);
        page.setResources(new PDResources(resources));
        return xObjects;
    }

    private static COSStream streamOf(PDImageXObject image) {
        COSBase object = image.getCOSObject();
        return object instanceof COSStream stream ? stream : null;
    }

    static double resampleFactor(double measuredPpi, int targetDpi) {
        if (!Double.isFinite(measuredPpi) || measuredPpi <= targetDpi * RESAMPLE_THRESHOLD) {
            return 1.0;
        }
        return targetDpi / measuredPpi;
    }

    private Outcome process(PDDocument document, Entry entry, CompressionLevel level,
                            Map<COSStream, Double> resolutions) {
        PDImageXObject image = entry.image();
        COSStream stream = streamOf(image);
        long original = stream == null ? 0 : stream.getLength();
        try {
            Double measured = stream == null ? null : resolutions.get(stream);
            if (stream == null || measured == null || original <= 0 || !isEligible(image)) {
                return new Outcome(null, original, original);
            }
            double factor = resampleFactor(measured, level.targetDpi());
            int subsampling = (int) Math.max(1, Math.min(MAX_SUBSAMPLING, Math.floor(1.0 / factor)));
            int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * factor));
            int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * factor));

            BufferedImage decoded = image.getImage(null, subsampling);
            BufferedImage scaled = prepare(decoded, targetWidth, targetHeight, isGray(image));
            PDImageXObject candidate = JPEGFactory.createFromImage(document, scaled, level.jpegQuality());
            COSStream candidateStream = streamOf(candidate);
            long candidateBytes = candidateStream == null ? Long.MAX_VALUE : candidateStream.getLength();

            if (candidateBytes < original * MIN_GAIN_RATIO) {
                return new Outcome(candidate, original, candidateBytes);
            }
            return new Outcome(null, original, original);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Image left untouched: {0}", e.getClass().getSimpleName());
            return new Outcome(null, original, original);
        }
    }

    private static boolean isEligible(PDImageXObject image) throws IOException {
        if (image.isStencil() || image.getBitsPerComponent() == 1) {
            return false;
        }
        if (image.getWidth() < MIN_SIDE_PIXELS || image.getHeight() < MIN_SIDE_PIXELS) {
            return false;
        }
        if (image.getSoftMask() != null || image.getMask() != null) {
            return false;
        }
        PDColorSpace colorSpace = image.getColorSpace();
        return colorSpace != null && colorSpace.getNumberOfComponents() != CMYK_COMPONENTS;
    }

    private static boolean isGray(PDImageXObject image) throws IOException {
        PDColorSpace colorSpace = image.getColorSpace();
        return colorSpace instanceof PDDeviceGray
                || colorSpace instanceof PDCalGray
                || colorSpace instanceof PDICCBased && colorSpace.getNumberOfComponents() == 1;
    }

    private static BufferedImage prepare(BufferedImage image, int width, int height, boolean gray) {
        boolean needsScaling = image.getWidth() != width || image.getHeight() != height;
        boolean needsGrayConversion = gray && image.getType() != BufferedImage.TYPE_BYTE_GRAY;
        if (!needsScaling && !needsGrayConversion) {
            return image;
        }
        return ImageScaler.scale(image, width, height, gray);
    }
}
