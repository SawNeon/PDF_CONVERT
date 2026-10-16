package br.com.pdflocal.service;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OfficeFileDetector {

    static final long MAX_BYTES = 200L * 1024 * 1024;
    static final int MAX_ENTRIES = 20_000;

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
        (byte) 0xE1};
    private static final byte[] RTF = "{\\rtf".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> WORD_OLE = Set.of("doc");
    private static final Set<String> EXCEL_OLE = Set.of("xls");
    private static final Set<String> POWERPOINT_OLE = Set.of("ppt");

    public OfficeKind detect(Path file) throws PdfLocalException {
        String extension = extensionOf(file);
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw unsupported();
            }
            byte[] head = readHead(file);
            return switch (extension) {
                case "docx" -> openXml(file, head, "word/document.xml", OfficeKind.WORD);
                case "xlsx" -> openXml(file, head, "xl/workbook.xml", OfficeKind.EXCEL);
                case "pptx" -> openXml(file, head, "ppt/presentation.xml", OfficeKind.POWERPOINT);
                case "odt" -> zipOnly(head, OfficeKind.WORD);
                case "rtf" -> startsWith(head, RTF) ? OfficeKind.WORD : fail();
                default -> legacy(extension, head);
            };
        } catch (IOException e) {
            throw new CorruptFileException(e);
        }
    }

    private static OfficeKind legacy(String extension, byte[] head) throws PdfLocalException {
        if (!startsWith(head, OLE)) {
            throw unsupported();
        }
        if (WORD_OLE.contains(extension)) {
            return OfficeKind.WORD;
        }
        if (EXCEL_OLE.contains(extension)) {
            return OfficeKind.EXCEL;
        }
        if (POWERPOINT_OLE.contains(extension)) {
            return OfficeKind.POWERPOINT;
        }
        throw unsupported();
    }

    private static OfficeKind zipOnly(byte[] head, OfficeKind kind) throws PdfLocalException {
        return startsWith(head, ZIP) ? kind : fail();
    }

    private static OfficeKind fail() throws PdfLocalException {
        throw unsupported();
    }

    private static OfficeKind openXml(Path file, byte[] head, String requiredPart, OfficeKind kind)
            throws IOException, PdfLocalException {
        if (!startsWith(head, ZIP)) {
            throw unsupported();
        }
        boolean found = false;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.size() > MAX_ENTRIES) {
                throw unsupported();
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName().toLowerCase(Locale.ROOT);
                if (name.endsWith("vbaproject.bin")) {
                    throw new PdfLocalException(Messages.CONVERT_MACROS);
                }
                found |= name.equals(requiredPart);
            }
        } catch (java.util.zip.ZipException e) {
            throw unsupported();
        }
        if (!found) {
            throw unsupported();
        }
        return kind;
    }

    private static byte[] readHead(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(OLE.length);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String extensionOf(Path file) {
        Path name = file.getFileName();
        String text = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
        int dot = text.lastIndexOf('.');
        return dot < 0 ? "" : text.substring(dot + 1);
    }

    private static PdfLocalException unsupported() {
        return new PdfLocalException(Messages.CONVERT_UNSUPPORTED);
    }
}
