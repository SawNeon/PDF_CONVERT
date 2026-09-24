package br.com.pdflocal.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record SourceDocument(UUID id, Path path, int pageCount) {

    public SourceDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative: " + pageCount);
        }
    }
}
