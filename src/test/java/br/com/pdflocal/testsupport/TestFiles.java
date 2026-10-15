package br.com.pdflocal.testsupport;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

public final class TestFiles {

    public static final Color RED = new Color(255, 0, 0);
    public static final Color GREEN = new Color(0, 255, 0);
    public static final Color BLUE = new Color(0, 0, 255);
    public static final Color YELLOW = new Color(255, 255, 0);

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    private TestFiles() {
    }

    public static Path pdf(Path directory, String name, String... pageLabels) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (String label : pageLabels) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    content.newLineAtOffset(72, 700);
                    content.showText(label);
                    content.endText();
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path encryptedPdf(Path directory, String name, String password) throws IOException {
        Path file = pdf(directory, name, "secret");
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(file.toFile());
        }
        return file;
    }

    public static Path signedPdf(Path directory, String name, String... pageLabels) throws IOException {
        Path file = pdf(directory, name, pageLabels);
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(file))) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("Test signer");
            document.addSignature(signature);
            document.save(file.toFile());
        }
        return file;
    }

    public static Path corruptPdf(Path directory, String name) throws IOException {
        byte[] garbage = new byte[2048];
        new Random(42).nextBytes(garbage);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        out.write(garbage);
        Path file = directory.resolve(name);
        Files.write(file, out.toByteArray());
        return file;
    }

    public static Path fakeFile(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    public static Path image(Path directory, String name, String format, int width, int height) throws IOException {
        Path file = directory.resolve(name);
        if (!ImageIO.write(quadrants(width, height), format, file.toFile())) {
            throw new IOException("No writer for format " + format);
        }
        return file;
    }

    public static Path jpegWithOrientation(Path directory, String name, int width, int height, int orientation)
            throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(quadrants(width, height), "jpg", jpeg);
        byte[] bytes = jpeg.toByteArray();

        int insertAt = 2;
        if ((bytes[2] & 0xFF) == 0xFF && (bytes[3] & 0xFF) == 0xE0) {
            insertAt = 4 + (((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF));
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(bytes, 0, insertAt);
        result.write(exifSegment(orientation));
        result.write(bytes, insertAt, bytes.length - insertAt);

        Path file = directory.resolve(name);
        Files.write(file, result.toByteArray());
        return file;
    }

    public static BufferedImage syntheticScan(int width, int height, long seed, boolean gray) {
        BufferedImage image = new BufferedImage(
                width, height, gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        Random random = new Random(seed);
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (240 + random.nextInt(11) - 5);
        }
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(30, 30, 30));
        int margin = width / 14;
        int line = Math.max(6, height / 160);
        for (int y = height / 12; y < height - height / 12; y += line * 3) {
            int x = margin;
            while (x < width - margin) {
                int length = line * (4 + random.nextInt(24));
                graphics.fillRect(x, y, Math.min(length, width - margin - x), line);
                x += length + line * 2;
            }
        }
        graphics.dispose();
        return image;
    }

    public static Path scannedPdf(Path directory, String name, int pages, int width, int height,
                                  float jpegQuality, boolean gray) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDImageXObject image = JPEGFactory.createFromImage(
                        document, syntheticScan(width, height, i + 1, gray), jpegQuality);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path scannedPageWithText(Path directory, String name, String text, int width, int height,
                                           float jpegQuality) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = JPEGFactory.createFromImage(
                    document, syntheticScan(width, height, 7, false), jpegQuality);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path sharedImagePdf(Path directory, String name, int width, int height) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDImageXObject image = JPEGFactory.createFromImage(document, syntheticScan(width, height, 3, false), 0.9f);
            for (int i = 0; i < 2; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path transparentImagePdf(Path directory, String name, int size) throws IOException {
        BufferedImage argb = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        graphics.setColor(new Color(200, 30, 30, 255));
        graphics.fillOval(0, 0, size, size);
        graphics.setColor(new Color(30, 30, 200, 90));
        graphics.fillRect(size / 4, size / 4, size / 2, size / 2);
        graphics.dispose();
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, argb);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 50, 200, 400, 400);
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path noisyJpegPdf(Path directory, String name, int size, float jpegQuality) throws IOException {
        Random random = new Random(11);
        BufferedImage noise = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) noise.getRaster().getDataBuffer()).getData();
        random.nextBytes(data);
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = JPEGFactory.createFromImage(document, noise, jpegQuality);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 50, 200, 400, 400);
            }
            document.save(file.toFile());
        }
        return file;
    }

    public static Path largeNoisyPng(Path directory, String name, int width, int height) throws IOException {
        Path file = directory.resolve(name);
        ImageIO.write(syntheticScan(width, height, 5, false), "png", file.toFile());
        return file;
    }

    public static List<PDImageXObject> imagesOf(PDDocument document) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject image) {
                    images.add(image);
                }
            }
        }
        return images;
    }

    public static double meanDifference(BufferedImage a, BufferedImage b) {
        int width = Math.min(a.getWidth(), b.getWidth());
        int height = Math.min(a.getHeight(), b.getHeight());
        long total = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color first = new Color(a.getRGB(x, y));
                Color second = new Color(b.getRGB(x, y));
                total += Math.abs(first.getRed() - second.getRed())
                        + Math.abs(first.getGreen() - second.getGreen())
                        + Math.abs(first.getBlue() - second.getBlue());
            }
        }
        return total / (3.0 * width * height);
    }

    public static BufferedImage render(Path pdf, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFRenderer(document).renderImage(pageIndex, 1.0f);
        }
    }

    public static Color dominantColor(int rgb) {
        Color pixel = new Color(rgb);
        Color best = RED;
        long bestDistance = Long.MAX_VALUE;
        for (Color candidate : List.of(RED, GREEN, BLUE, YELLOW)) {
            long dr = pixel.getRed() - candidate.getRed();
            long dg = pixel.getGreen() - candidate.getGreen();
            long db = pixel.getBlue() - candidate.getBlue();
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    public static List<String> pageTexts(Path pdf) throws IOException {
        List<String> texts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                texts.add(stripper.getText(document).strip());
            }
        }
        return texts;
    }

    public static String sha256(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BufferedImage quadrants(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        graphics.setColor(RED);
        graphics.fillRect(0, 0, halfWidth, halfHeight);
        graphics.setColor(GREEN);
        graphics.fillRect(halfWidth, 0, width - halfWidth, halfHeight);
        graphics.setColor(BLUE);
        graphics.fillRect(0, halfHeight, halfWidth, height - halfHeight);
        graphics.setColor(YELLOW);
        graphics.fillRect(halfWidth, halfHeight, width - halfWidth, height - halfHeight);
        graphics.dispose();
        return image;
    }

    private static byte[] exifSegment(int orientation) {
        byte[] tiff = {
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
        int length = 2 + EXIF_HEADER.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(EXIF_HEADER);
        out.writeBytes(tiff);
        return out.toByteArray();
    }
}
