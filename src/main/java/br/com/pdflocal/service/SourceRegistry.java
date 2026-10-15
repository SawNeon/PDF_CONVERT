package br.com.pdflocal.service;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class SourceRegistry implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SourceRegistry.class.getName());

    private record Entry(SourceDocument source, PDDocument document, ReentrantLock lock) {
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final FileTypeDetector detector = new FileTypeDetector();

    public SourceDocument open(Path file) throws PdfLocalException {
        return open(file, null);
    }

    public SourceDocument open(Path file, char[] password) throws PdfLocalException {
        Objects.requireNonNull(file, "file");
        if (detector.detect(file) != FileType.PDF) {
            throw new UnsupportedFileException();
        }
        PDDocument document = PdfLoader.open(file, password);
        SourceDocument source = new SourceDocument(UUID.randomUUID(), file, document.getNumberOfPages(), PdfLoader.hasSignature(document));
        entries.put(source.id(), new Entry(source, document, new ReentrantLock()));
        LOG.log(Level.INFO, "PDF opened with {0} pages", source.pageCount());
        return source;
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public SourceDocument source(UUID id) {
        return entry(id).source();
    }

    public PDDocument document(UUID id) {
        return entry(id).document();
    }

    public ReentrantLock lock(UUID id) {
        return entry(id).lock();
    }

    public boolean anySigned(Collection<PageItem> items) {
        return items.stream()
                .filter(item -> !item.isImage())
                .map(PageItem::sourceId)
                .distinct()
                .anyMatch(id -> contains(id) && source(id).signed());
    }

    public Collection<SourceDocument> sources() {
        return entries.values().stream().map(Entry::source).toList();
    }

    public void close(UUID id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return;
        }
        entry.lock().lock();
        try {
            if (entries.remove(id, entry)) {
                closeQuietly(entry.document());
            }
        } finally {
            entry.lock().unlock();
        }
    }

    @Override
    public void close() {
        List<UUID> ids = List.copyOf(entries.keySet());
        ids.forEach(this::close);
    }

    private Entry entry(UUID id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown source document");
        }
        return entry;
    }

    private static void closeQuietly(PDDocument document) {
        try {
            document.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to close PDF: {0}", e.getClass().getSimpleName());
        }
    }
}
