package br.com.pdflocal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class PageEdits {

    private PageEdits() {
    }

    public static List<PageItem> rotate(List<PageItem> items, Collection<Integer> indices, int degrees) {
        Set<Integer> selected = validated(items, indices);
        List<PageItem> result = new ArrayList<>(items);
        for (int index : selected) {
            result.set(index, items.get(index).rotatedBy(degrees));
        }
        return result;
    }

    public static List<PageItem> delete(List<PageItem> items, Collection<Integer> indices) {
        Set<Integer> selected = validated(items, indices);
        List<PageItem> result = new ArrayList<>(items.size() - selected.size());
        for (int i = 0; i < items.size(); i++) {
            if (!selected.contains(i)) {
                result.add(items.get(i));
            }
        }
        return result;
    }

    private static Set<Integer> validated(List<PageItem> items, Collection<Integer> indices) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(indices, "indices");
        Set<Integer> selected = new TreeSet<>(indices);
        selected.forEach(index -> Objects.checkIndex(index, items.size()));
        return selected;
    }
}
