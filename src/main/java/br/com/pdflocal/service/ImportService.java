package br.com.pdflocal.service;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.WrongPasswordException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ImportService {

    private static final Logger LOG = Logger.getLogger(ImportService.class.getName());

    private final SourceRegistry registry;
    private final FileTypeDetector detector = new FileTypeDetector();

    public ImportService(SourceRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ImportResult importFiles(List<Path> files) {
        Objects.requireNonNull(files, "files");
        List<PageItem> items = new ArrayList<>();
        List<ImportResult.Failure> failures = new ArrayList<>();

        for (Path file : files) {
            try {
                items.addAll(importFile(file));
            } catch (PdfLocalException e) {
                failures.add(failure(file, e));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Unexpected import failure: {0}", e.getClass().getSimpleName());
                failures.add(new ImportResult.Failure(displayName(file), Messages.get(Messages.UNEXPECTED)));
            }
        }
        return new ImportResult(items, failures);
    }

    public ImportResult importProtected(Path file, char[] password) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(password, "password");
        try {
            return new ImportResult(importFile(file, password), List.of());
        } catch (PdfLocalException e) {
            return new ImportResult(List.of(), List.of(failure(file, e)));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Unexpected import failure: {0}", e.getClass().getSimpleName());
            return new ImportResult(List.of(),
                    List.of(new ImportResult.Failure(displayName(file), Messages.get(Messages.UNEXPECTED))));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static ImportResult.Failure failure(Path file, PdfLocalException error) {
        ImportResult.Reason reason = error instanceof WrongPasswordException ? ImportResult.Reason.PASSWORD_WRONG
                : error instanceof PasswordProtectedException ? ImportResult.Reason.PASSWORD_REQUIRED
                : ImportResult.Reason.OTHER;
        return new ImportResult.Failure(displayName(file), error.getMessage(), file, reason);
    }

    private List<PageItem> importFile(Path file) throws PdfLocalException {
        return importFile(file, null);
    }

    private List<PageItem> importFile(Path file, char[] password) throws PdfLocalException {
        FileType type = detector.detect(file);
        if (type.isImage()) {
            return List.of(PageItem.image(file));
        }

        SourceDocument source = registry.open(file, password);
        if (source.pageCount() == 0) {
            registry.close(source.id());
            throw new PdfLocalException(Messages.FILE_UNREADABLE);
        }
        List<PageItem> pages = new ArrayList<>(source.pageCount());
        for (int index = 0; index < source.pageCount(); index++) {
            pages.add(PageItem.pdfPage(source.id(), index));
        }
        return pages;
    }

    private static String displayName(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }
}
