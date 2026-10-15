package br.com.pdflocal.service;

import br.com.pdflocal.util.CorruptFileException;
import br.com.pdflocal.util.PasswordProtectedException;
import br.com.pdflocal.util.PdfLocalException;
import br.com.pdflocal.util.WrongPasswordException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

final class PdfLoader {

    private static final Logger LOG = Logger.getLogger(PdfLoader.class.getName());

    private PdfLoader() {
    }

    static PDDocument open(Path file, char[] password) throws PdfLocalException {
        boolean hasPassword = password != null && password.length > 0;
        try {
            return Loader.loadPDF(file.toFile(), hasPassword ? new String(password) : "", null, null,
                    IOUtils.createTempFileOnlyStreamCache());
        } catch (InvalidPasswordException e) {
            throw hasPassword ? new WrongPasswordException(e) : new PasswordProtectedException(e);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to read PDF: {0}", e.getClass().getSimpleName());
            throw new CorruptFileException(e);
        }
    }

    static boolean hasSignature(PDDocument document) {
        try {
            return !document.getSignatureDictionaries().isEmpty();
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Signature check failed: {0}", e.getClass().getSimpleName());
            return false;
        }
    }
}
