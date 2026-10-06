package br.com.pdflocal.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PageGroups {

    private PageGroups() {
    }

    public static List<PageGroup> of(List<PageItem> items, ViewMode mode) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(mode, "mode");
        List<PageGroup> groups = new ArrayList<>();
        int start = 0;
        while (start < items.size()) {
            int end = start + 1;
            if (mode == ViewMode.BY_FILE) {
                while (end < items.size() && sameFile(items.get(start), items.get(end))) {
                    end++;
                }
            }
            groups.add(new PageGroup(start, items.subList(start, end)));
            start = end;
        }
        return groups;
    }

    private static boolean sameFile(PageItem a, PageItem b) {
        return !a.isImage() && !b.isImage() && a.sourceId().equals(b.sourceId());
    }
}
