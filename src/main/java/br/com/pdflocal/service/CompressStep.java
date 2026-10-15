package br.com.pdflocal.service;

import br.com.pdflocal.model.CompressionLevel;
import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class CompressStep implements OutputStep {

    private final CompressionLevel level;
    private final PdfCompressor compressor = new PdfCompressor();
    private CompressionStats stats = CompressionStats.EMPTY;

    public CompressStep(CompressionLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        stats = compressor.compress(document, level, listener);
    }

    public CompressionStats stats() {
        return stats;
    }
}
