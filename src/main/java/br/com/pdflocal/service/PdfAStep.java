package br.com.pdflocal.service;

import br.com.pdflocal.util.SaveCancelledException;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

public final class PdfAStep implements OutputStep {

    private static final String PROFILE_NAME = "sRGB IEC61966-2.1";
    private static final String PROFILE_REGISTRY = "http://www.color.org";
    private static final String PRODUCER = "UniPdf";

    @Override
    public void apply(PDDocument document, SaveListener listener) throws IOException, SaveCancelledException {
        int pages = document.getNumberOfPages();
        PageRasterizer rasterizer = new PageRasterizer(document);
        for (int index = 0; index < pages; index++) {
            if (listener.isCancelled()) {
                throw new SaveCancelledException();
            }
            listener.onTransform(index, pages);
            PageRasterizer.Rendered rendered = rasterizer.render(index, PageRasterizer.DEFAULT_DPI);
            rasterizer.replace(index, rendered.image(), rendered.pointsPerPixel());
        }
        listener.onTransform(pages, pages);
        addOutputIntent(document);
        addMetadata(document);
    }

    @Override
    public boolean requiresClassicStructure() {
        return true;
    }

    private static void addOutputIntent(PDDocument document) throws IOException {
        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        PDOutputIntent intent = new PDOutputIntent(document, new ByteArrayInputStream(profile));
        intent.setInfo(PROFILE_NAME);
        intent.setOutputCondition(PROFILE_NAME);
        intent.setOutputConditionIdentifier(PROFILE_NAME);
        intent.setRegistryName(PROFILE_REGISTRY);
        document.getDocumentCatalog().addOutputIntent(intent);
    }

    private static void addMetadata(PDDocument document) throws IOException {
        String now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String xmp = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "  <rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n"
                + "   <pdfaid:part>1</pdfaid:part>\n"
                + "   <pdfaid:conformance>B</pdfaid:conformance>\n"
                + "  </rdf:Description>\n"
                + "  <rdf:Description rdf:about=\"\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">\n"
                + "   <pdf:Producer>" + PRODUCER + "</pdf:Producer>\n"
                + "  </rdf:Description>\n"
                + "  <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n"
                + "   <xmp:CreatorTool>" + PRODUCER + "</xmp:CreatorTool>\n"
                + "   <xmp:CreateDate>" + now + "</xmp:CreateDate>\n"
                + "   <xmp:ModifyDate>" + now + "</xmp:ModifyDate>\n"
                + "   <xmp:MetadataDate>" + now + "</xmp:MetadataDate>\n"
                + "  </rdf:Description>\n"
                + " </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>";
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        document.getDocumentCatalog().setMetadata(metadata);
    }
}
