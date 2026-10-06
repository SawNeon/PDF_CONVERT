package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

public final class DocumentComposer {

    private static final long LOW_SPACE_BYTES = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(DocumentComposer.class.getName());

    private final ImageToPageService imageService = new ImageToPageService();

    public void compose(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize, Path output)
            throws PdfLocalException {
        compose(items, registry, imagePageSize, output, SaveListener.NONE);
    }

    public void compose(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize, Path output,
                        SaveListener listener) throws PdfLocalException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(imagePageSize, "imagePageSize");
        Objects.requireNonNull(output, "output");

        validate(items, registry, output);

        Path directory = output.toAbsolutePath().getParent();
        Path temporary = null;
        List<ReentrantLock> locks = List.of();
        try {
            temporary = Files.createTempFile(directory, "pdflocal-", ".tmp");
            locks = lockSources(items, registry);
            try (PDDocument result = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                int done = 0;
                for (PageItem item : items) {
                    throwIfCancelled(listener);
                    append(result, item, registry, imagePageSize);
                    listener.onPage(++done, items.size());
                }
                throwIfCancelled(listener);
                result.save(temporary.toFile());
            }
            throwIfCancelled(listener);
            moveAtomically(temporary, output);
            temporary = null;
            LOG.log(Level.INFO, "PDF saved with {0} pages", items.size());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to save PDF: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(classify(e, usableSpace(directory)), e);
        } finally {
            unlock(locks);
            deleteQuietly(temporary);
        }
    }

    static String classify(Exception error, long usableSpace) {
        if (error instanceof AccessDeniedException) {
            return Messages.SAVE_DENIED;
        }
        if (error instanceof IOException && usableSpace >= 0 && usableSpace < LOW_SPACE_BYTES) {
            return Messages.SAVE_NO_SPACE;
        }
        return Messages.SAVE_FAILED;
    }

    private static long usableSpace(Path directory) {
        try {
            return Files.getFileStore(directory).getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private static void throwIfCancelled(SaveListener listener) throws SaveCancelledException {
        if (listener.isCancelled()) {
            throw new SaveCancelledException();
        }
    }

    private static List<ReentrantLock> lockSources(List<PageItem> items, SourceRegistry registry) {
        List<UUID> ids = items.stream()
                .filter(item -> !item.isImage())
                .map(PageItem::sourceId)
                .distinct()
                .sorted()
                .toList();
        List<ReentrantLock> acquired = new ArrayList<>();
        try {
            for (UUID id : ids) {
                ReentrantLock lock = registry.lock(id);
                lock.lock();
                acquired.add(lock);
            }
        } catch (RuntimeException e) {
            unlock(acquired);
            throw e;
        }
        return acquired;
    }

    private static void unlock(List<ReentrantLock> locks) {
        for (int i = locks.size() - 1; i >= 0; i--) {
            locks.get(i).unlock();
        }
    }

    private void append(PDDocument result, PageItem item, SourceRegistry registry, PageSize imagePageSize)
            throws IOException, PdfLocalException {
        PDPage page;
        if (item.isImage()) {
            page = imageService.addPage(result, item.imagePath(), imagePageSize);
        } else {
            page = result.importPage(registry.document(item.sourceId()).getPage(item.pageIndex()));
        }
        page.setRotation(Math.floorMod(page.getRotation() + item.rotation(), 360));
    }

    private static void validate(List<PageItem> items, SourceRegistry registry, Path output)
            throws PdfLocalException {
        if (items.isEmpty()) {
            throw new PdfLocalException(Messages.SAVE_EMPTY);
        }

        List<Path> inputs = new ArrayList<>();
        registry.sources().forEach(source -> inputs.add(source.path()));
        for (PageItem item : items) {
            if (item.isImage()) {
                inputs.add(item.imagePath());
            } else if (!registry.contains(item.sourceId())
                    || item.pageIndex() >= registry.source(item.sourceId()).pageCount()) {
                throw new PdfLocalException(Messages.PAGE_MISSING);
            }
        }

        Path directory = output.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new PdfLocalException(Messages.SAVE_FOLDER_MISSING);
        }
        for (Path input : inputs) {
            if (isSameFile(input, output)) {
                throw new PdfLocalException(Messages.SAVE_OVERWRITES_SOURCE);
            }
        }
    }

    private static boolean isSameFile(Path a, Path b) {
        try {
            return Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to delete temporary file: {0}", e.getClass().getSimpleName());
        }
    }
}
