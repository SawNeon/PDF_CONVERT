package br.com.pdflocal.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExifOrientation {

    public static final int NORMAL = 1;

    private static final Logger LOG = Logger.getLogger(ExifOrientation.class.getName());

    public int read(Path image) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(image.toFile());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                if (orientation >= 1 && orientation <= 8) {
                    return orientation;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "EXIF orientation not available: {0}", e.getClass().getSimpleName());
        }
        return NORMAL;
    }

    public static boolean swapsAxes(int orientation) {
        return orientation >= 5;
    }

    public static double[] toDisplayed(int orientation, double x, double y, double width, double height) {
        return switch (orientation) {
            case 2 -> new double[] {width - x, y};
            case 3 -> new double[] {width - x, height - y};
            case 4 -> new double[] {x, height - y};
            case 5 -> new double[] {y, x};
            case 6 -> new double[] {height - y, x};
            case 7 -> new double[] {height - y, width - x};
            case 8 -> new double[] {y, width - x};
            default -> new double[] {x, y};
        };
    }
}
