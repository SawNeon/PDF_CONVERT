package br.com.pdflocal.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class GridSelection {

    private final Set<UUID> selected = new LinkedHashSet<>();
    private UUID anchor;

    void click(List<UUID> order, int index, boolean ctrl, boolean shift) {
        UUID key = order.get(index);
        int anchorIndex = anchor == null ? -1 : order.indexOf(anchor);

        if (shift && anchorIndex >= 0) {
            if (!ctrl) {
                selected.clear();
            }
            int from = Math.min(anchorIndex, index);
            int to = Math.max(anchorIndex, index);
            selected.addAll(order.subList(from, to + 1));
        } else if (ctrl) {
            if (!selected.remove(key)) {
                selected.add(key);
            }
            anchor = key;
        } else {
            selected.clear();
            selected.add(key);
            anchor = key;
        }
    }

    void selectOnly(UUID key) {
        selected.clear();
        selected.add(key);
        anchor = key;
    }

    void selectAll(List<UUID> order) {
        selected.clear();
        selected.addAll(order);
        anchor = order.isEmpty() ? null : order.get(0);
    }

    void set(Collection<UUID> keys) {
        selected.clear();
        selected.addAll(keys);
        anchor = keys.isEmpty() ? null : keys.iterator().next();
    }

    void retain(Collection<UUID> keys) {
        selected.retainAll(keys);
        if (anchor != null && !keys.contains(anchor)) {
            anchor = null;
        }
    }

    void clear() {
        selected.clear();
        anchor = null;
    }

    boolean isSelected(UUID key) {
        return selected.contains(key);
    }

    boolean isEmpty() {
        return selected.isEmpty();
    }

    int size() {
        return selected.size();
    }

    Set<UUID> keys() {
        return Set.copyOf(selected);
    }

    List<Integer> indices(List<UUID> order) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            if (selected.contains(order.get(i))) {
                result.add(i);
            }
        }
        return result;
    }
}
