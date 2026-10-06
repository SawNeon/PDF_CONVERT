package br.com.pdflocal.model;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public record PageGroup(int start, List<PageItem> items) {

    public PageGroup {
        if (start < 0 || items.isEmpty()) {
            throw new IllegalArgumentException("A group needs a non-negative start and at least one item");
        }
        items = List.copyOf(items);
    }

    public UUID key() {
        return first().id();
    }

    public PageItem first() {
        return items.get(0);
    }

    public int end() {
        return start + items.size();
    }

    public List<Integer> indices() {
        return IntStream.range(start, end()).boxed().toList();
    }
}
