package br.com.pdflocal.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class OfficeFixtures {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOCUMENT_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";

    private OfficeFixtures() {
    }

    public static Path docx(Path directory, String name, String text) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", XML + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + DOCUMENT_REL + "\" Target=\"word/document.xml\"/></Relationships>");
        parts.put("word/document.xml", XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>");
        return zip(directory.resolve(name), parts);
    }

    public static Path xlsx(Path directory, String name, String text) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", XML + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + DOCUMENT_REL + "\" Target=\"xl/workbook.xml\"/></Relationships>");
        parts.put("xl/workbook.xml", XML + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Plan1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels", XML + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                + "Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put("xl/worksheets/sheet1.xml", XML + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>" + text + "</t></is></c></row></sheetData></worksheet>");
        return zip(directory.resolve(name), parts);
    }

    public static Path html(Path directory, String name, String body) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>t</title></head><body>"
                + body + "</body></html>", StandardCharsets.UTF_8);
        return file;
    }

    private static Path zip(Path file, Map<String, String> parts) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }
}
