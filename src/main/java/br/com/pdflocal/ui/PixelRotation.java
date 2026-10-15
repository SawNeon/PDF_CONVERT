package br.com.pdflocal.ui;

final class PixelRotation {

    record Rotated(int[] pixels, int width, int height) {
    }

    private PixelRotation() {
    }

    static Rotated rotate(int[] pixels, int width, int height, int degrees) {
        int turns = Math.floorMod(degrees / 90, 4);
        if (turns == 0) {
            return new Rotated(pixels, width, height);
        }
        boolean swap = turns % 2 == 1;
        int newWidth = swap ? height : width;
        int newHeight = swap ? width : height;
        int[] result = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = pixels[y * width + x];
                int nx;
                int ny;
                switch (turns) {
                    case 1 -> {
                        nx = height - 1 - y;
                        ny = x;
                    }
                    case 2 -> {
                        nx = width - 1 - x;
                        ny = height - 1 - y;
                    }
                    default -> {
                        nx = y;
                        ny = width - 1 - x;
                    }
                }
                result[ny * newWidth + nx] = value;
            }
        }
        return new Rotated(result, newWidth, newHeight);
    }
}
