package br.com.pdflocal.service;

import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;

public interface OutputStep {

    void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException;
}
