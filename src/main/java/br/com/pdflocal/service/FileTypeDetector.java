package br.com.pdflocal.service;

import br.com.pdflocal.model.FileType;
import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.UnsupportedFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FileTypeDetector {

    private static final Map<FileType, byte[][]> SIGNATURES = signatures();
    private static final int HEADER_LENGTH = 8;

    public FileType detect(Path file) throws PdfLocalException {
        byte[] header;
        try (InputStream in = Files.newInputStream(file)) {
            header = in.readNBytes(HEADER_LENGTH);
        } catch (IOException e) {
            throw new CorruptFileException(e);
        }
        return detect(header);
    }

    FileType detect(byte[] header) throws UnsupportedFileException {
        for (Map.Entry<FileType, byte[][]> entry : SIGNATURES.entrySet()) {
            for (byte[] signature : entry.getValue()) {
                if (startsWith(header, signature)) {
                    return entry.getKey();
                }
            }
        }
        throw new UnsupportedFileException();
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }

    private static Map<FileType, byte[][]> signatures() {
        Map<FileType, byte[][]> map = new LinkedHashMap<>();
        map.put(FileType.PDF, new byte[][] {ascii("%PDF-")});
        map.put(FileType.JPEG, new byte[][] {{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}});
        map.put(FileType.PNG, new byte[][] {{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}});
        map.put(FileType.GIF, new byte[][] {ascii("GIF87a"), ascii("GIF89a")});
        map.put(FileType.BMP, new byte[][] {ascii("BM")});
        map.put(FileType.TIFF, new byte[][] {{0x49, 0x49, 0x2A, 0x00}, {0x4D, 0x4D, 0x00, 0x2A}});
        return map;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
