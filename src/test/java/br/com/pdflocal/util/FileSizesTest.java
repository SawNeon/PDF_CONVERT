package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileSizesTest {

    @Test
    void smallSizesAreShownInBytes() {
        assertEquals("0 B", FileSizes.format(0));
        assertEquals("512 B", FileSizes.format(512));
        assertEquals("1023 B", FileSizes.format(1023));
    }

    @Test
    void kilobytesAreRoundedToWholeNumbers() {
        assertEquals("1 KB", FileSizes.format(1024));
        assertEquals("850 KB", FileSizes.format(850L * 1024));
        assertEquals("1024 KB", FileSizes.format(1024L * 1024 - 1));
    }

    @Test
    void megabytesUseOneDecimalWithACommaSeparator() {
        assertEquals("1,0 MB", FileSizes.format(1024L * 1024));
        assertEquals("6,4 MB", FileSizes.format(Math.round(6.42 * 1024 * 1024)));
        assertEquals("12,3 MB", FileSizes.format(Math.round(12.3 * 1024 * 1024)));
    }

    @Test
    void gigabytesAreSupported() {
        assertEquals("1,5 GB", FileSizes.format(Math.round(1.5 * 1024 * 1024 * 1024)));
    }

    @Test
    void negativeValuesAreTreatedAsZero() {
        assertEquals("0 B", FileSizes.format(-5));
    }
}
