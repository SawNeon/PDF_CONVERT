package br.com.pdflocal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageGroupsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void byPageEveryItemIsItsOwnGroup() {
        List<PageItem> items = List.of(PageItem.pdfPage(a, 0), PageItem.pdfPage(a, 1), PageItem.image(Path.of("x.png")));

        List<PageGroup> groups = PageGroups.of(items, ViewMode.BY_PAGE);

        assertEquals(3, groups.size());
        assertEquals(List.of(0), groups.get(0).indices());
        assertEquals(List.of(2), groups.get(2).indices());
    }

    @Test
    void byFileConsecutivePagesOfTheSamePdfFormOneGroup() {
        List<PageItem> items = List.of(
                PageItem.pdfPage(a, 0), PageItem.pdfPage(a, 1), PageItem.pdfPage(a, 2),
                PageItem.pdfPage(b, 0), PageItem.pdfPage(b, 1));

        List<PageGroup> groups = PageGroups.of(items, ViewMode.BY_FILE);

        assertEquals(2, groups.size());
        assertEquals(List.of(0, 1, 2), groups.get(0).indices());
        assertEquals(List.of(3, 4), groups.get(1).indices());
        assertEquals(3, groups.get(0).items().size());
        assertEquals(items.get(0).id(), groups.get(0).key());
        assertEquals(items.get(3), groups.get(1).first());
    }

    @Test
    void byFileEachImageIsAGroupOfItsOwn() {
        List<PageItem> items = List.of(
                PageItem.image(Path.of("x.png")), PageItem.image(Path.of("x.png")), PageItem.pdfPage(a, 0));

        List<PageGroup> groups = PageGroups.of(items, ViewMode.BY_FILE);

        assertEquals(3, groups.size());
    }

    @Test
    void byFilePagesOfTheSamePdfSplitByOtherFilesFormSeparateGroups() {
        List<PageItem> items = List.of(PageItem.pdfPage(a, 0), PageItem.pdfPage(b, 0), PageItem.pdfPage(a, 1));

        List<PageGroup> groups = PageGroups.of(items, ViewMode.BY_FILE);

        assertEquals(3, groups.size());
    }

    @Test
    void groupsCoverTheWholeListWithoutGaps() {
        List<PageItem> items = List.of(
                PageItem.pdfPage(a, 0), PageItem.pdfPage(a, 1), PageItem.image(Path.of("x.png")),
                PageItem.pdfPage(b, 0), PageItem.pdfPage(b, 1), PageItem.pdfPage(b, 2));

        List<PageGroup> groups = PageGroups.of(items, ViewMode.BY_FILE);

        int expectedStart = 0;
        for (PageGroup group : groups) {
            assertEquals(expectedStart, group.start());
            expectedStart = group.end();
        }
        assertEquals(items.size(), expectedStart);
    }

    @Test
    void emptyListHasNoGroups() {
        assertTrue(PageGroups.of(List.of(), ViewMode.BY_FILE).isEmpty());
        assertTrue(PageGroups.of(List.of(), ViewMode.BY_PAGE).isEmpty());
    }

    @Test
    void aGroupNeedsAtLeastOneItem() {
        assertThrows(IllegalArgumentException.class, () -> new PageGroup(0, List.of()));
    }
}
