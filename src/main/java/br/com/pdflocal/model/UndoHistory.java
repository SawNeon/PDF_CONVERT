package br.com.pdflocal.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class UndoHistory {

    public static final int DEFAULT_LIMIT = 30;

    private final Deque<List<PageItem>> snapshots = new ArrayDeque<>();
    private final int limit;

    public UndoHistory() {
        this(DEFAULT_LIMIT);
    }

    public UndoHistory(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        this.limit = limit;
    }

    public void push(List<PageItem> snapshot) {
        snapshots.addLast(List.copyOf(snapshot));
        while (snapshots.size() > limit) {
            snapshots.removeFirst();
        }
    }

    public Optional<List<PageItem>> pop() {
        return Optional.ofNullable(snapshots.pollLast());
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    public void clear() {
        snapshots.clear();
    }

    public Set<UUID> referencedSources() {
        Set<UUID> sources = new HashSet<>();
        for (List<PageItem> snapshot : snapshots) {
            for (PageItem item : snapshot) {
                if (!item.isImage()) {
                    sources.add(item.sourceId());
                }
            }
        }
        return sources;
    }
}
