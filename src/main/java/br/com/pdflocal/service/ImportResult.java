package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import java.nio.file.Path;
import java.util.List;

public record ImportResult(List<PageItem> items, List<Failure> failures) {

    public enum Reason {
        OTHER,
        PASSWORD_REQUIRED,
        PASSWORD_WRONG
    }

    public record Failure(String fileName, String message, Path file, Reason reason) {

        public Failure(String fileName, String message) {
            this(fileName, message, null, Reason.OTHER);
        }

        public boolean needsPassword() {
            return reason != Reason.OTHER && file != null;
        }
    }

    public ImportResult {
        items = List.copyOf(items);
        failures = List.copyOf(failures);
    }
}
