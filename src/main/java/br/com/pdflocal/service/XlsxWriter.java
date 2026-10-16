package br.com.pdflocal.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XlsxWriter {

    record Sheet(String name, List<List<String>> rows) {
    }

    static final int MAX_SHEET_NAME = 31;
    static final int MAX_CELL_CHARACTERS = 32_767;
    static final int MAX_COLUMNS = 16_384;

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final double MIN_WIDTH = 8;
    private static final double MAX_WIDTH = 60;

    private XlsxWriter() {
    }

    static void write(Path file, List<Sheet> sheets) throws IOException {
        if (sheets.isEmpty()) {
            throw new IOException("A workbook needs at least one sheet");
        }
        List<String> names = uniqueNames(sheets);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            entry(zip, "_rels/.rels", XML + "<Relationships xmlns=\"" + PACKAGE_RELS + "\"><Relationship Id=\"rId1\" "
                    + "Type=\"" + OFFICE_RELS + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml", workbook(names));
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelations(sheets.size()));
            for (int index = 0; index < sheets.size(); index++) {
                entry(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", worksheet(sheets.get(index)));
            }
        }
    }

    static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    static String sanitizeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
        while (cleaned.startsWith("'") || cleaned.endsWith("'")) {
            cleaned = cleaned.replaceAll("^'+|'+$", "").strip();
        }
        if (cleaned.isEmpty()) {
            cleaned = "Planilha";
        }
        return cleaned.length() > MAX_SHEET_NAME ? cleaned.substring(0, MAX_SHEET_NAME).strip() : cleaned;
    }

    static String escape(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int length = Math.min(text.length(), MAX_CELL_CHARACTERS);
        for (int i = 0; i < length; ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!isValidXmlCharacter(codePoint)) {
                continue;
            }
            switch (codePoint) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                default -> result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }

    private static boolean isValidXmlCharacter(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    private static List<String> uniqueNames(List<Sheet> sheets) {
        List<String> names = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Sheet sheet : sheets) {
            String base = sanitizeSheetName(sheet.name());
            String candidate = base;
            int counter = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                String suffix = " (" + counter++ + ")";
                int room = MAX_SHEET_NAME - suffix.length();
                candidate = (base.length() > room ? base.substring(0, room) : base) + suffix;
            }
            names.add(candidate);
        }
        return names;
    }

    private static String contentTypes(int sheets) {
        StringBuilder xml = new StringBuilder(XML)
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheets; i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String workbook(List<String> names) {
        StringBuilder xml = new StringBuilder(XML).append("<workbook xmlns=\"").append(MAIN)
                .append("\" xmlns:r=\"").append(OFFICE_RELS).append("\"><sheets>");
        for (int i = 0; i < names.size(); i++) {
            xml.append("<sheet name=\"").append(escape(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private static String workbookRelations(int sheets) {
        StringBuilder xml = new StringBuilder(XML).append("<Relationships xmlns=\"").append(PACKAGE_RELS).append("\">");
        for (int i = 1; i <= sheets; i++) {
            xml.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(OFFICE_RELS)
                    .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        return xml.append("</Relationships>").toString();
    }

    private static String worksheet(Sheet sheet) {
        int columns = Math.min(MAX_COLUMNS, sheet.rows().stream().mapToInt(List::size).max().orElse(0));
        double[] widths = new double[columns];
        for (List<String> row : sheet.rows()) {
            for (int column = 0; column < Math.min(columns, row.size()); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length() + 2.0);
            }
        }
        StringBuilder xml = new StringBuilder(XML).append("<worksheet xmlns=\"").append(MAIN).append("\">");
        if (columns > 0) {
            xml.append("<cols>");
            for (int column = 0; column < columns; column++) {
                double width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widths[column]));
                xml.append("<col min=\"").append(column + 1).append("\" max=\"").append(column + 1)
                        .append("\" width=\"").append(String.format(Locale.ROOT, "%.1f", width))
                        .append("\" customWidth=\"1\"/>");
            }
            xml.append("</cols>");
        }
        xml.append("<sheetData>");
        int rowNumber = 0;
        for (List<String> row : sheet.rows()) {
            rowNumber++;
            xml.append("<row r=\"").append(rowNumber).append("\">");
            for (int column = 0; column < Math.min(columns, row.size()); column++) {
                String text = row.get(column);
                if (text.isBlank()) {
                    continue;
                }
                String reference = columnName(column) + rowNumber;
                Optional<String> number = CellValues.asNumber(text);
                if (number.isPresent()) {
                    xml.append("<c r=\"").append(reference).append("\"><v>").append(number.get()).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(text)).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
