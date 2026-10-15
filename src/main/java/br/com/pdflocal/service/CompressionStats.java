package br.com.pdflocal.service;

public record CompressionStats(int images, int replaced, long bytesBefore, long bytesAfter) {

    public static final CompressionStats EMPTY = new CompressionStats(0, 0, 0, 0);

    public long savedBytes() {
        return Math.max(0, bytesBefore - bytesAfter);
    }
}
