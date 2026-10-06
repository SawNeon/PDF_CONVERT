package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class ThumbnailService implements AutoCloseable {

    public static final int DPI = 50;
    public static final int DEFAULT_CACHE_SIZE = 200;
    public static final int IMAGE_MAX_SIDE = 585;

    private static final int IMAGE_THREADS = 2;
    private static final Logger LOG = Logger.getLogger(ThumbnailService.class.getName());

    private record Key(UUID sourceId, int pageIndex, Path imagePath) {

        static Key of(PageItem item) {
            return new Key(item.sourceId(), item.pageIndex(), item.imagePath());
        }
    }

    @FunctionalInterface
    private interface Rendering {
        BufferedImage render() throws IOException;
    }

    private final class Worker {

        private final ExecutorService executor;
        private final UUID sourceId;
        private volatile boolean released;
        private PDFRenderer renderer;

        Worker(UUID sourceId) {
            this.sourceId = sourceId;
            this.executor = Executors.newSingleThreadExecutor(threadFactory("thumbnail-pdf"));
        }

        BufferedImage render(int pageIndex) throws IOException {
            if (released) {
                throw new CancellationException();
            }
            ReentrantLock lock = registry.lock(sourceId);
            lock.lock();
            try {
                if (renderer == null) {
                    renderer = new PDFRenderer(registry.document(sourceId));
                    renderer.setSubsamplingAllowed(true);
                }
                return renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            } finally {
                lock.unlock();
            }
        }

        void release() {
            released = true;
            executor.shutdown();
        }
    }

    private final SourceRegistry registry;
    private final int dpi;
    private final int imageMaxSide;
    private final ExifOrientation exifOrientation = new ExifOrientation();
    private final Map<Key, BufferedImage> cache;
    private final Map<Key, CompletableFuture<BufferedImage>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Worker> workers = new ConcurrentHashMap<>();
    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(IMAGE_THREADS, threadFactory("thumbnail-image"));
    private volatile boolean closed;

    public ThumbnailService(SourceRegistry registry) {
        this(registry, DEFAULT_CACHE_SIZE);
    }

    public ThumbnailService(SourceRegistry registry, int cacheSize) {
        this(registry, cacheSize, DPI);
    }

    public ThumbnailService(SourceRegistry registry, int cacheSize, int dpi) {
        if (cacheSize < 1) {
            throw new IllegalArgumentException("cacheSize must be positive: " + cacheSize);
        }
        if (dpi < 1) {
            throw new IllegalArgumentException("dpi must be positive: " + dpi);
        }
        this.dpi = dpi;
        this.imageMaxSide = Math.round(IMAGE_MAX_SIDE * (float) dpi / DPI);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> eldest) {
                return size() > cacheSize;
            }
        });
    }

    public CompletableFuture<BufferedImage> thumbnail(PageItem item) {
        Objects.requireNonNull(item, "item");
        Key key = Key.of(item);

        BufferedImage cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<BufferedImage> created = new CompletableFuture<>();
        CompletableFuture<BufferedImage> existing = pending.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }

        try {
            submit(key, item, created);
        } catch (RejectedExecutionException e) {
            pending.remove(key, created);
            created.completeExceptionally(e);
        }
        return created;
    }

    public void release(UUID sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        Worker worker = workers.remove(sourceId);
        if (worker != null) {
            worker.release();
        }
        synchronized (cache) {
            cache.keySet().removeIf(key -> sourceId.equals(key.sourceId()));
        }
        pending.entrySet().removeIf(entry -> {
            if (sourceId.equals(entry.getKey().sourceId())) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });
    }

    @Override
    public void close() {
        closed = true;
        workers.values().forEach(Worker::release);
        workers.clear();
        imageExecutor.shutdown();
        cache.clear();
        pending.values().forEach(future -> future.cancel(false));
        pending.clear();
    }

    private void submit(Key key, PageItem item, CompletableFuture<BufferedImage> future) {
        if (closed) {
            throw new RejectedExecutionException("ThumbnailService is closed");
        }
        if (item.isImage()) {
            imageExecutor.execute(() -> complete(key, future, () -> renderImage(item.imagePath())));
        } else {
            Worker worker = workers.computeIfAbsent(item.sourceId(), Worker::new);
            worker.executor.execute(() -> complete(key, future, () -> worker.render(item.pageIndex())));
        }
    }

    private BufferedImage renderImage(Path file) throws IOException {
        return ImageThumbnail.create(file, imageMaxSide, exifOrientation.read(file));
    }

    private void complete(Key key, CompletableFuture<BufferedImage> future, Rendering rendering) {
        try {
            BufferedImage image = rendering.render();
            cache.put(key, image);
            future.complete(image);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Thumbnail failed: {0}", e.getClass().getSimpleName());
            future.completeExceptionally(e);
        } finally {
            pending.remove(key, future);
        }
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
