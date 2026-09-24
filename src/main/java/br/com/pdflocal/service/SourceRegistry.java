package br.com.pdflocal.service;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PasswordProtectedException;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

public final class SourceRegistry implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SourceRegistry.class.getName());

    private record Entry(SourceDocument source, PDDocument document) {
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final FileTypeDetector detector = new FileTypeDetector();

    public SourceDocument open(Path file) throws PdfLocalException {
        Objects.requireNonNull(file, "file");
        if (detector.detect(file) != FileType.PDF) {
            throw new UnsupportedFileException();
        }
        PDDocument document = load(file);
        SourceDocument source = new SourceDocument(UUID.randomUUID(), file, document.getNumberOfPages());
        entries.put(source.id(), new Entry(source, document));
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

    public Collection<SourceDocument> sources() {
        return entries.values().stream().map(Entry::source).toList();
    }

    public void close(UUID id) {
        Entry entry = entries.remove(id);
        if (entry != null) {
            closeQuietly(entry.document());
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

    private static PDDocument load(Path file) throws PdfLocalException {
        try {
            return Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache());
        } catch (InvalidPasswordException e) {
            throw new PasswordProtectedException(e);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to read PDF: {0}", e.getClass().getSimpleName());
            throw new CorruptFileException(e);
        }
    }

    private static void closeQuietly(PDDocument document) {
        try {
            document.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to close PDF: {0}", e.getClass().getSimpleName());
        }
    }
}
