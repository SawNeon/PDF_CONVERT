package br.com.pdflocal.service;

import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

final class TableExtractor {

    static final long MAX_CELLS = 2_000_000L;
    static final int MAX_BLOCKS_PER_PAGE = 100_000;
    static final int MAX_COLUMNS = 100;

    private static final double MIN_CELL_GAP = 8.0;
    private static final double CELL_GAP_FACTOR = 0.9;
    private static final double WORD_GAP_FACTOR = 0.2;
    private static final double LINE_TOLERANCE_FACTOR = 0.45;
    private static final double COLUMN_TOLERANCE = 4.0;
    private static final double CELL_HEIGHT_FACTOR = 5.0;
    private static final double ROW_OVERLAP = 0.5;
    private static final int MIN_TABLE_ROWS_WITH_TWO_COLUMNS = 3;

    private record Block(String text, double left, double right, double top, double bottom, double size, int line) {

        double height() {
            return bottom - top;
        }
    }

    private record Placed(int column, int block) {
    }

    private record Item(double top, List<List<String>> rows) {
    }

    private static final class Column {

        private final boolean byRight;
        private final double left;
        private final double right;
        private final double center;

        Column(boolean byRight, double left, double right, double center) {
            this.byRight = byRight;
            this.left = left;
            this.right = right;
            this.center = center;
        }

        double distanceTo(Block block) {
            return byRight ? Math.abs(block.right() - right) : Math.abs(block.left() - left);
        }
    }

    private static final class Collector extends PDFTextStripper {

        private final List<TextPosition> positions = new ArrayList<>();

        Collector() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            positions.addAll(textPositions);
        }
    }

    List<XlsxWriter.Sheet> extract(PDDocument document, SaveListener listener)
            throws IOException, SaveCancelledException {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        int pages = document.getNumberOfPages();
        long cells = 0;
        for (int index = 0; index < pages; index++) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(index, pages);
            Collector collector = new Collector();
            collector.setStartPage(index + 1);
            collector.setEndPage(index + 1);
            collector.writeText(document, Writer.nullWriter());
            List<List<String>> rows = rowsOf(blocksOf(collector.positions));
            for (List<String> row : rows) {
                cells += row.size();
            }
            if (cells > MAX_CELLS) {
                throw new IOException("The document has too many cells");
            }
            if (!rows.isEmpty()) {
                sheets.add(new XlsxWriter.Sheet("Página " + (index + 1), rows));
            }
        }
        listener.onTransform(pages, pages);
        return sheets;
    }

    private static List<Block> blocksOf(List<TextPosition> positions) throws IOException {
        List<TextPosition> visible = new ArrayList<>();
        for (TextPosition position : positions) {
            String text = position.getUnicode();
            if (text != null && !text.isBlank()) {
                visible.add(position);
            }
        }
        visible.sort(Comparator.comparingDouble(TextPosition::getYDirAdj).thenComparingDouble(TextPosition::getXDirAdj));
        List<List<TextPosition>> lines = new ArrayList<>();
        List<TextPosition> current = null;
        double lineY = 0;
        for (TextPosition position : visible) {
            double tolerance = Math.max(1.0, position.getHeightDir() * LINE_TOLERANCE_FACTOR);
            if (current == null || Math.abs(position.getYDirAdj() - lineY) > tolerance) {
                current = new ArrayList<>();
                lines.add(current);
                lineY = position.getYDirAdj();
            }
            current.add(position);
        }
        List<Block> blocks = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            List<TextPosition> line = lines.get(lineIndex);
            line.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
            splitLine(line, lineIndex, blocks);
            if (blocks.size() > MAX_BLOCKS_PER_PAGE) {
                throw new IOException("The page has too many text blocks");
            }
        }
        return blocks;
    }

    private static void splitLine(List<TextPosition> line, int lineIndex, List<Block> blocks) {
        StringBuilder text = new StringBuilder();
        double start = 0;
        double end = 0;
        double top = 0;
        double bottom = 0;
        double size = 0;
        boolean open = false;
        for (TextPosition position : line) {
            double x = position.getXDirAdj();
            double fontSize = Math.max(1.0, position.getFontSizeInPt());
            double gap = x - end;
            if (open && gap > Math.max(MIN_CELL_GAP, fontSize * CELL_GAP_FACTOR)) {
                add(blocks, text, start, end, top, bottom, size, lineIndex);
                open = false;
            }
            double glyphTop = position.getYDirAdj() - position.getHeightDir();
            if (!open) {
                text.setLength(0);
                start = x;
                top = glyphTop;
                bottom = position.getYDirAdj();
                size = fontSize;
                end = x;
                open = true;
            } else {
                if (gap > fontSize * WORD_GAP_FACTOR && text.length() > 0 && text.charAt(text.length() - 1) != ' ') {
                    text.append(' ');
                }
                top = Math.min(top, glyphTop);
                bottom = Math.max(bottom, position.getYDirAdj());
                size = Math.max(size, fontSize);
            }
            text.append(position.getUnicode());
            end = Math.max(end, x + position.getWidthDirAdj());
        }
        if (open) {
            add(blocks, text, start, end, top, bottom, size, lineIndex);
        }
    }

    private static void add(List<Block> blocks, StringBuilder text, double start, double end, double top,
                            double bottom, double size, int line) {
        String value = text.toString().strip();
        if (!value.isEmpty()) {
            blocks.add(new Block(value, start, end, top, bottom, size, line));
        }
        text.setLength(0);
    }

    private static List<List<String>> rowsOf(List<Block> blocks) {
        boolean[] consumed = new boolean[blocks.size()];
        List<Item> items = new ArrayList<>(tablesOf(blocks, consumed));
        int[] lastLine = {-1};
        List<String> loose = new ArrayList<>();
        double[] looseTop = {0};
        for (int index = 0; index < blocks.size(); index++) {
            if (consumed[index]) {
                continue;
            }
            Block block = blocks.get(index);
            if (block.line() != lastLine[0] && !loose.isEmpty()) {
                items.add(new Item(looseTop[0], List.of(new ArrayList<>(loose))));
                loose.clear();
            }
            if (loose.isEmpty()) {
                looseTop[0] = block.top();
            }
            lastLine[0] = block.line();
            loose.add(block.text());
        }
        if (!loose.isEmpty()) {
            items.add(new Item(looseTop[0], List.of(new ArrayList<>(loose))));
        }
        items.sort(Comparator.comparingDouble(Item::top));
        List<List<String>> rows = new ArrayList<>();
        for (Item item : items) {
            rows.addAll(item.rows());
        }
        return rows;
    }

    private static boolean cellLike(Block block) {
        return block.height() <= block.size() * CELL_HEIGHT_FACTOR && !block.text().isBlank();
    }

    private static List<Column> columnsOf(List<Block> blocks) {
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            if (cellLike(blocks.get(index))) {
                candidates.add(index);
            }
        }
        candidates.sort(Comparator.comparingDouble(index -> blocks.get(index).left()));
        List<Column> columns = new ArrayList<>();
        boolean[] inColumn = new boolean[blocks.size()];
        for (List<Integer> cluster : cluster(candidates, blocks, false)) {
            if (cluster.size() >= 2) {
                columns.add(columnOf(cluster, blocks, false));
                cluster.forEach(member -> inColumn[member] = true);
            }
        }
        List<Integer> orphans = new ArrayList<>();
        for (int index : candidates) {
            if (!inColumn[index]) {
                orphans.add(index);
            }
        }
        orphans.sort(Comparator.comparingDouble(index -> blocks.get(index).right()));
        for (List<Integer> cluster : cluster(orphans, blocks, true)) {
            if (cluster.size() >= 2) {
                columns.add(columnOf(cluster, blocks, true));
            }
        }
        columns.sort(Comparator.comparingDouble(column -> column.center));
        return columns;
    }

    private static List<List<Integer>> cluster(List<Integer> sorted, List<Block> blocks, boolean byRight) {
        List<List<Integer>> clusters = new ArrayList<>();
        double mean = 0;
        for (int index : sorted) {
            double edge = byRight ? blocks.get(index).right() : blocks.get(index).left();
            List<Integer> last = clusters.isEmpty() ? null : clusters.get(clusters.size() - 1);
            if (last != null && Math.abs(edge - mean) <= COLUMN_TOLERANCE) {
                mean = (mean * last.size() + edge) / (last.size() + 1);
                last.add(index);
            } else {
                clusters.add(new ArrayList<>(List.of(index)));
                mean = edge;
            }
        }
        return clusters;
    }

    private static Column columnOf(List<Integer> members, List<Block> blocks, boolean byRight) {
        double left = 0;
        double right = 0;
        double center = 0;
        for (int member : members) {
            Block block = blocks.get(member);
            left += block.left();
            right += block.right();
            center += (block.left() + block.right()) / 2;
        }
        int count = members.size();
        return new Column(byRight, left / count, right / count, center / count);
    }

    private static int columnIndex(Block block, List<Column> columns) {
        int best = -1;
        double distance = COLUMN_TOLERANCE;
        for (int index = 0; index < columns.size(); index++) {
            double away = columns.get(index).distanceTo(block);
            if (away <= distance) {
                distance = away;
                best = index;
            }
        }
        return best;
    }

    private static List<Item> tablesOf(List<Block> blocks, boolean[] consumed) {
        List<Column> columns = columnsOf(blocks);
        if (columns.size() < 2) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>();
        int[] columnOf = new int[blocks.size()];
        Arrays.fill(columnOf, -1);
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            if (cellLike(block)) {
                columnOf[index] = columnIndex(block, columns);
                if (columnOf[index] >= 0) {
                    order.add(index);
                }
            }
        }
        order.sort(Comparator.comparingDouble((Integer index) -> blocks.get(index).top())
                .thenComparingDouble(index -> blocks.get(index).left()));
        List<List<Placed>> rows = new ArrayList<>();
        for (int index : order) {
            Block block = blocks.get(index);
            List<Placed> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (last != null && sameRow(blocks.get(last.get(0).block()), block)) {
                last.add(new Placed(columnOf[index], index));
            } else {
                rows.add(new ArrayList<>(List.of(new Placed(columnOf[index], index))));
            }
        }
        List<Item> tables = new ArrayList<>();
        int position = 0;
        while (position < rows.size()) {
            if (distinctColumns(rows.get(position)) < 2) {
                position++;
                continue;
            }
            int start = position;
            while (position < rows.size() && distinctColumns(rows.get(position)) >= 2) {
                position++;
            }
            List<List<Placed>> run = new ArrayList<>();
            TreeSet<Integer> runColumns = new TreeSet<>();
            for (List<Placed> row : rows.subList(start, position)) {
                row.forEach(placed -> runColumns.add(placed.column()));
            }
            if (start > 0 && rows.get(start - 1).size() == 1 && spans(rows.get(start - 1).get(0), runColumns, columns,
                    blocks)) {
                run.add(rows.get(start - 1));
            }
            run.addAll(rows.subList(start, position));
            if (position < rows.size() && rows.get(position).size() == 1 && spans(rows.get(position).get(0), runColumns,
                    columns, blocks)) {
                run.add(rows.get(position));
            }
            flush(run, runColumns, blocks, consumed, tables);
        }
        return tables;
    }

    private static boolean sameRow(Block first, Block second) {
        double overlap = Math.min(first.bottom(), second.bottom()) - Math.max(first.top(), second.top());
        return overlap > ROW_OVERLAP * Math.min(first.height(), second.height());
    }

    private static int distinctColumns(List<Placed> row) {
        return (int) row.stream().map(Placed::column).distinct().count();
    }

    private static boolean spans(Placed placed, TreeSet<Integer> runColumns, List<Column> columns, List<Block> blocks) {
        if (!runColumns.contains(placed.column())) {
            return false;
        }
        Block block = blocks.get(placed.block());
        for (int other : runColumns) {
            double left = columns.get(other).left;
            if (other != placed.column() && left > block.left() + COLUMN_TOLERANCE
                    && left < block.right() - COLUMN_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static void flush(List<List<Placed>> run, TreeSet<Integer> runColumns, List<Block> blocks,
                              boolean[] consumed, List<Item> tables) {
        int count = runColumns.size();
        boolean accepted = (count >= 3 || (count == 2 && run.size() >= MIN_TABLE_ROWS_WITH_TWO_COLUMNS))
                && count <= MAX_COLUMNS;
        if (!accepted) {
            return;
        }
        List<Integer> slotOf = new ArrayList<>(runColumns);
        List<List<String>> output = new ArrayList<>();
        double top = Double.MAX_VALUE;
        for (List<Placed> row : run) {
            String[] cells = new String[count];
            for (Placed placed : row) {
                int slot = slotOf.indexOf(placed.column());
                Block block = blocks.get(placed.block());
                cells[slot] = cells[slot] == null ? block.text() : cells[slot] + " " + block.text();
                consumed[placed.block()] = true;
                top = Math.min(top, block.top());
            }
            List<String> values = new ArrayList<>(count);
            for (String cell : cells) {
                values.add(cell == null ? "" : cell);
            }
            output.add(values);
        }
        tables.add(new Item(top, output));
    }
}
