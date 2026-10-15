package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class ImageShare {

    public static final double LOW_SHARE = 0.30;

    private static final int MAX_FORM_DEPTH = 12;
    private static final Logger LOG = Logger.getLogger(ImageShare.class.getName());

    private final long imageBytes;
    private final long totalBytes;

    private ImageShare(long imageBytes, long totalBytes) {
        this.imageBytes = imageBytes;
        this.totalBytes = totalBytes;
    }

    public static ImageShare of(long imageBytes, long totalBytes) {
        return new ImageShare(Math.max(0, imageBytes), Math.max(0, totalBytes));
    }

    public long imageBytes() {
        return imageBytes;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public double share() {
        if (totalBytes <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) imageBytes / totalBytes);
    }

    public int percent() {
        return (int) Math.round(share() * 100);
    }

    public boolean isLow() {
        return share() < LOW_SHARE;
    }

    public static ImageShare measure(List<PageItem> items, SourceRegistry registry) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(registry, "registry");

        long images = 0;
        long total = 0;
        Set<Path> countedImageFiles = new LinkedHashSet<>();
        Map<UUID, List<Integer>> pagesBySource = new LinkedHashMap<>();
        for (PageItem item : items) {
            if (item.isImage()) {
                if (countedImageFiles.add(item.imagePath())) {
                    long size = sizeOf(item.imagePath());
                    images += size;
                    total += size;
                }
            } else {
                pagesBySource.computeIfAbsent(item.sourceId(), id -> new ArrayList<>()).add(item.pageIndex());
            }
        }
        for (Map.Entry<UUID, List<Integer>> entry : pagesBySource.entrySet()) {
            if (!registry.contains(entry.getKey())) {
                continue;
            }
            total += sizeOf(registry.source(entry.getKey()).path());
            images += imageBytesOf(entry.getKey(), entry.getValue(), registry);
        }
        return new ImageShare(images, total);
    }

    private static long imageBytesOf(UUID sourceId, List<Integer> pageIndices, SourceRegistry registry) {
        ReentrantLock lock = registry.lock(sourceId);
        lock.lock();
        try {
            Set<COSStream> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            long bytes = 0;
            for (int index : pageIndices) {
                PDResources resources = registry.document(sourceId).getPage(index).getResources();
                bytes += imageBytes(resources, seen, 0);
            }
            return bytes;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Image share could not be measured: {0}", e.getClass().getSimpleName());
            return 0;
        } finally {
            lock.unlock();
        }
    }

    private static long imageBytes(PDResources resources, Set<COSStream> seen, int depth) throws IOException {
        if (resources == null || depth > MAX_FORM_DEPTH) {
            return 0;
        }
        long bytes = 0;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            COSBase object = xObject == null ? null : xObject.getCOSObject();
            if (!(object instanceof COSStream stream) || !seen.add(stream)) {
                continue;
            }
            if (xObject instanceof PDImageXObject) {
                bytes += stream.getLength();
            } else if (xObject instanceof PDFormXObject form) {
                bytes += imageBytes(form.getResources(), seen, depth + 1);
            }
        }
        return bytes;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }
}
