package br.com.pdflocal.ui;

import br.com.pdflocal.model.PageEdits;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageOrder;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.model.UndoHistory;
import br.com.pdflocal.service.DocumentComposer;
import br.com.pdflocal.service.ImportResult;
import br.com.pdflocal.service.ImportService;
import br.com.pdflocal.service.SaveListener;
import br.com.pdflocal.service.SourceRegistry;
import br.com.pdflocal.service.ThumbnailService;
import br.com.pdflocal.util.PdfLocalException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

final class DocumentSession implements AutoCloseable {

    private static final int PREVIEW_DPI = 110;
    private static final int PREVIEW_CACHE_SIZE = 6;

    private final SourceRegistry registry = new SourceRegistry();
    private final ThumbnailService thumbnails = new ThumbnailService(registry);
    private final ThumbnailService previews = new ThumbnailService(registry, PREVIEW_CACHE_SIZE, PREVIEW_DPI);
    private final ImportService importer = new ImportService(registry);
    private final DocumentComposer composer = new DocumentComposer();
    private final ObservableList<PageItem> items = FXCollections.observableArrayList();
    private final UndoHistory history = new UndoHistory();
    private final Set<UUID> knownSources = new HashSet<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pdflocal-io");
        thread.setDaemon(true);
        return thread;
    });

    ObservableList<PageItem> items() {
        return items;
    }

    int fileCount() {
        return (int) items.stream()
                .map(item -> item.isImage() ? item.imagePath() : item.sourceId())
                .distinct()
                .count();
    }

    CompletableFuture<ImportResult> importFiles(List<Path> files) {
        List<Path> selected = List.copyOf(files);
        return CompletableFuture.supplyAsync(() -> importer.importFiles(selected), background);
    }

    void append(List<PageItem> newItems) {
        newItems.stream().filter(item -> !item.isImage()).map(PageItem::sourceId).forEach(knownSources::add);
        items.addAll(newItems);
    }

    void rotate(Collection<Integer> indices, int degrees) {
        if (!indices.isEmpty()) {
            edit(PageEdits.rotate(items, indices, degrees));
        }
    }

    void delete(Collection<Integer> indices) {
        if (!indices.isEmpty()) {
            edit(PageEdits.delete(items, indices));
        }
    }

    void move(Collection<Integer> indices, int insertionIndex) {
        if (!indices.isEmpty()) {
            edit(PageOrder.moveMany(items, indices, insertionIndex));
        }
    }

    boolean undo() {
        Optional<List<PageItem>> previous = history.pop();
        previous.ifPresent(list -> {
            items.setAll(list);
            releaseUnreferencedSources();
        });
        return previous.isPresent();
    }

    boolean canUndo() {
        return !history.isEmpty();
    }

    CompletableFuture<Void> clear() {
        List<UUID> sourceIds = registry.sources().stream().map(SourceDocument::id).collect(Collectors.toList());
        history.clear();
        knownSources.clear();
        items.clear();
        return CompletableFuture.runAsync(() -> sourceIds.forEach(this::closeSource), background);
    }

    record SaveOperation(CompletableFuture<Integer> result, Runnable cancel) {
    }

    CompletableFuture<Integer> save(Path output, PageSize imagePageSize) {
        return startSave(output, imagePageSize, (done, total) -> { }).result();
    }

    SaveOperation startSave(Path output, PageSize imagePageSize, BiConsumer<Integer, Integer> progress) {
        List<PageItem> snapshot = List.copyOf(items);
        AtomicBoolean cancelled = new AtomicBoolean();
        SaveListener listener = new SaveListener() {
            @Override
            public void onPage(int done, int total) {
                progress.accept(done, total);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
        CompletableFuture<Integer> result = CompletableFuture.supplyAsync(() -> {
            try {
                composer.compose(snapshot, registry, imagePageSize, output, listener);
                return snapshot.size();
            } catch (PdfLocalException e) {
                throw new CompletionException(e);
            }
        }, background);
        return new SaveOperation(result, () -> cancelled.set(true));
    }

    boolean hasSignedSources() {
        return registry.anySigned(items);
    }

    String displayName(PageItem item) {
        Path path = item.isImage() ? item.imagePath() : sourcePath(item.sourceId());
        Path name = path == null ? null : path.getFileName();
        return name == null ? "" : name.toString();
    }

    CompletableFuture<Image> thumbnail(PageItem item) {
        return thumbnails.thumbnail(item).thenApply(image -> SwingFXUtils.toFXImage(image, null));
    }

    CompletableFuture<Image> preview(PageItem item) {
        return previews.thumbnail(item).thenApply(image -> SwingFXUtils.toFXImage(image, null));
    }

    int openSourceCount() {
        return registry.sources().size();
    }

    CompletableFuture<Void> whenIdle() {
        return CompletableFuture.runAsync(() -> { }, background);
    }

    @Override
    public void close() {
        thumbnails.close();
        previews.close();
        background.shutdown();
        registry.close();
    }

    private void edit(List<PageItem> next) {
        if (next.equals(items)) {
            return;
        }
        history.push(items);
        items.setAll(next);
        releaseUnreferencedSources();
    }

    private void releaseUnreferencedSources() {
        Set<UUID> referenced = history.referencedSources();
        items.stream().filter(item -> !item.isImage()).forEach(item -> referenced.add(item.sourceId()));
        List<UUID> unused = knownSources.stream().filter(id -> !referenced.contains(id)).toList();
        if (unused.isEmpty()) {
            return;
        }
        knownSources.removeAll(unused);
        background.execute(() -> unused.forEach(this::closeSource));
    }

    private void closeSource(UUID id) {
        thumbnails.release(id);
        previews.release(id);
        registry.close(id);
    }

    private Path sourcePath(UUID sourceId) {
        return registry.contains(sourceId) ? registry.source(sourceId).path() : null;
    }
}
