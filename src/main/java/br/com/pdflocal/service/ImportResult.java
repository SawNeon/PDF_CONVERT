package br.com.pdflocal.service;

import br.com.pdflocal.model.PageItem;
import java.util.List;

public record ImportResult(List<PageItem> items, List<Failure> failures) {

    public record Failure(String fileName, String message) {
    }

    public ImportResult {
        items = List.copyOf(items);
        failures = List.copyOf(failures);
    }
}
