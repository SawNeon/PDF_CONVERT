package br.com.pdflocal.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BrandAssetsTest {

    private static final Pattern CSS_IMAGE = Pattern.compile("url\\(\"(images/[^\"]+)\"\\)");

    @Test
    void everyImageReferencedByTheStylesheetExistsAndIsAValidPng() throws Exception {
        String css;
        try (InputStream in = getClass().getResourceAsStream("/pdflocal.css")) {
            assertNotNull(in);
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> referenced = new ArrayList<>();
        Matcher matcher = CSS_IMAGE.matcher(css);
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }

        assertFalse(referenced.isEmpty());
        for (String path : referenced) {
            BufferedImage image = read("/" + path);
            assertTrue(image.getColorModel().hasAlpha(), path + " must have a transparent background");
        }
    }

    @Test
    void windowIconsExistInTheDeclaredSizes() throws Exception {
        List<Integer> sizes = new ArrayList<>();
        for (String path : AppIcons.PATHS) {
            BufferedImage image = read(path);
            assertEquals(image.getWidth(), image.getHeight(), path);
            sizes.add(image.getWidth());
        }

        assertEquals(List.of(32, 64, 256), sizes);
    }

    @Test
    void logoHasTransparentCornersAndOpaqueContent() throws Exception {
        BufferedImage logo = read("/images/logo-green.png");

        assertEquals(0, logo.getRGB(0, 0) >>> 24);
        boolean hasOpaque = false;
        for (int x = 0; x < logo.getWidth() && !hasOpaque; x += 4) {
            for (int y = 0; y < logo.getHeight(); y += 4) {
                if ((logo.getRGB(x, y) >>> 24) == 255) {
                    hasOpaque = true;
                    break;
                }
            }
        }
        assertTrue(hasOpaque);
    }

    @Test
    void watermarksAreFaint() throws Exception {
        for (String path : List.of("/images/watermark-light.png", "/images/watermark-dark.png")) {
            BufferedImage image = read(path);
            int maxAlpha = 0;
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    maxAlpha = Math.max(maxAlpha, image.getRGB(x, y) >>> 24);
                }
            }
            assertTrue(maxAlpha > 0 && maxAlpha <= 40, path + " max alpha " + maxAlpha);
        }
    }

    @Test
    void executableIconContainsAllSizes() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("packaging", "app-icon.ico"));
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0, header.getShort(0));
        assertEquals(1, header.getShort(2));
        assertEquals(7, header.getShort(4));
    }

    private BufferedImage read(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, resource);
            return image;
        }
    }
}
