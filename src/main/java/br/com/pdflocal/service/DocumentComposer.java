package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

public final class DocumentComposer {

    private static final Logger LOG = Logger.getLogger(DocumentComposer.class.getName());

    private final ImageToPageService imageService = new ImageToPageService();

    public void compose(List<PageItem> items, SourceRegistry registry, PageSize imagePageSize, Path output)
            throws PdfLocalException {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(imagePageSize, "imagePageSize");
        Objects.requireNonNull(output, "output");

        validate(items, registry, output);

        Path directory = output.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, "pdflocal-", ".tmp");
            try (PDDocument result = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                for (PageItem item : items) {
                    append(result, item, registry, imagePageSize);
                }
                result.save(temporary.toFile());
            }
            moveAtomically(temporary, output);
            temporary = null;
            LOG.log(Level.INFO, "PDF saved with {0} pages", items.size());
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to save PDF: {0}", e.getClass().getSimpleName());
            throw new PdfLocalException(Messages.SAVE_FAILED, e);
        } finally {
            deleteQuietly(temporary);
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
            throw new PdfLocalException(Messages.SAVE_FAILED);
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
