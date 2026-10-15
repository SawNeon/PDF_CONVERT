package br.com.pdflocal.service;

import br.com.pdflocal.util.SaveCancelledException;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

final class ImageResolutionFinder extends PDFStreamEngine {

    private static final Logger LOG = Logger.getLogger(ImageResolutionFinder.class.getName());

    private static final int MAX_FORM_DEPTH = 12;
    private static final double POINTS_PER_INCH = 72.0;
    private static final double MIN_EXTENT_INCHES = 1e-6;

    private final Map<COSStream, Double> lowestPpi = new IdentityHashMap<>();
    private final Set<COSStream> unmeasurable = Collections.newSetFromMap(new IdentityHashMap<>());
    private int formDepth;

    ImageResolutionFinder() {
        addOperator(new Concatenate(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
    }

    Map<COSStream, Double> measure(PDDocument document, SaveListener listener) throws SaveCancelledException {
        for (PDPage page : document.getPages()) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            formDepth = 0;
            try {
                processPage(page);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.FINE, "Page could not be measured: {0}", e.getClass().getSimpleName());
                markPageUnmeasurable(page);
            }
        }
        Map<COSStream, Double> result = new IdentityHashMap<>(lowestPpi);
        result.keySet().removeAll(unmeasurable);
        return result;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) {
            PDXObject xObject = getResources().getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                recordUse(image);
            } else if (xObject instanceof PDFormXObject form) {
                showForm(form);
            }
            return;
        }
        super.processOperator(operator, operands);
    }

    @Override
    public void showForm(PDFormXObject form) throws IOException {
        if (formDepth >= MAX_FORM_DEPTH) {
            throw new IOException("Form nesting too deep");
        }
        formDepth++;
        try {
            super.showForm(form);
        } finally {
            formDepth--;
        }
    }

    private void recordUse(PDImageXObject image) {
        if (!(image.getCOSObject() instanceof COSStream stream)) {
            return;
        }
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        double widthInches = ctm.getScalingFactorX() / POINTS_PER_INCH;
        double heightInches = ctm.getScalingFactorY() / POINTS_PER_INCH;
        if (!(widthInches > MIN_EXTENT_INCHES && heightInches > MIN_EXTENT_INCHES)
                || image.getWidth() <= 0 || image.getHeight() <= 0) {
            unmeasurable.add(stream);
            return;
        }
        double ppi = Math.min(image.getWidth() / widthInches, image.getHeight() / heightInches);
        if (!Double.isFinite(ppi)) {
            unmeasurable.add(stream);
            return;
        }
        lowestPpi.merge(stream, ppi, Math::min);
    }

    private void markPageUnmeasurable(PDPage page) {
        PDResources resources = page.getResources();
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            try {
                if (resources.getXObject(name) instanceof PDImageXObject image
                        && image.getCOSObject() instanceof COSStream stream) {
                    unmeasurable.add(stream);
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.FINE, "XObject skipped: {0}", e.getClass().getSimpleName());
            }
        }
    }
}
