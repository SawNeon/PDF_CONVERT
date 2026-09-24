package br.com.pdflocal.model;

public enum FileType {
    PDF,
    JPEG,
    PNG,
    GIF,
    BMP,
    TIFF;

    public boolean isImage() {
        return this != PDF;
    }
}
