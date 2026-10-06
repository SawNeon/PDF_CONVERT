package br.com.pdflocal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class PageOrder {

    private PageOrder() {
    }

    public static <T> List<T> move(List<T> items, int from, int insertionIndex) {
        Objects.requireNonNull(items, "items");
        Objects.checkIndex(from, items.size());
        return moveMany(items, List.of(from), insertionIndex);
    }

    public static <T> List<T> moveMany(List<T> items, Collection<Integer> indices, int insertionIndex) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(indices, "indices");
        if (insertionIndex < 0 || insertionIndex > items.size()) {
            throw new IndexOutOfBoundsException("Insertion index out of range: " + insertionIndex);
        }
        TreeSet<Integer> selected = new TreeSet<>(indices);
        selected.forEach(index -> Objects.checkIndex(index, items.size()));

        List<T> moved = new ArrayList<>(selected.size());
        List<T> remaining = new ArrayList<>(items.size() - selected.size());
        for (int i = 0; i < items.size(); i++) {
            if (selected.contains(i)) {
                moved.add(items.get(i));
            } else {
                remaining.add(items.get(i));
            }
        }

        int before = selected.headSet(insertionIndex).size();
        List<T> result = new ArrayList<>(remaining);
        result.addAll(insertionIndex - before, moved);
        return result;
    }
}
