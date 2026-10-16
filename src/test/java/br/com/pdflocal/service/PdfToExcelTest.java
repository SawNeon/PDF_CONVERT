package br.com.pdflocal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.pdflocal.model.OfficeKind;
import br.com.pdflocal.model.PageItem;
import br.com.pdflocal.model.PageSize;
import br.com.pdflocal.model.SourceDocument;
import br.com.pdflocal.testsupport.TempDirCleanup;
import br.com.pdflocal.testsupport.TestFiles;
import br.com.pdflocal.util.Messages;
import br.com.pdflocal.util.PdfLocalException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TempDirCleanup.class)
class PdfToExcelTest {

    private static final Pattern CELL = Pattern.compile("<c r=\"([A-Z]+)(\\d+)\"[^>]*?(?:/>|>(.*?)</c>)", Pattern.DOTALL);

    private final SourceRegistry registry = new SourceRegistry();
    private final PdfToOffice converter = new PdfToOffice();

    @TempDir
    Path directory;

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void aTableWithLeftAlignedColumnsBecomesRowsAndColumns() throws Exception {
        Path pdf = pdf("tabela.pdf", page -> {
            text(page, 72, 700, "Relatorio de beneficiarios");
            row(page, 660, new float[] {72, 220, 360}, "Nome", "Plano", "Cidade");
            row(page, 640, new float[] {72, 220, 360}, "Maria da Silva", "Basico", "Varginha");
            row(page, 620, new float[] {72, 220, 360}, "Joao Souza", "Pleno", "Alfenas");
            row(page, 600, new float[] {72, 220, 360}, "Ana Lima", "Basico", "Tres Coracoes");
        });

        List<List<String>> rows = firstSheet(convert(pdf));

        assertEquals(List.of("Relatorio de beneficiarios"), rows.get(0));
        assertEquals(List.of("Nome", "Plano", "Cidade"), rows.get(1));
        assertEquals(List.of("Maria da Silva", "Basico", "Varginha"), rows.get(2));
        assertEquals(List.of("Joao Souza", "Pleno", "Alfenas"), rows.get(3));
        assertEquals(List.of("Ana Lima", "Basico", "Tres Coracoes"), rows.get(4));
    }

    @Test
    void rightAlignedNumbersStayInTheirColumn() throws Exception {
        Path pdf = pdf("valores.pdf", page -> {
            row(page, 700, new float[] {72, 250, 400}, "Item", "Qtd", "Valor");
            right(page, 680, 72, "Consulta", 250, "2", 460, "1.234,56");
            right(page, 660, 72, "Exame", 250, "10", 460, "89,90");
            right(page, 640, 72, "Internacao", 250, "100", 460, "12.000,00");
        });

        List<List<String>> rows = firstSheet(convert(pdf));

        assertEquals(4, rows.size());
        assertEquals("Consulta", rows.get(1).get(0));
        assertEquals("2", rows.get(1).get(1));
        assertEquals("1234.56", rows.get(1).get(2));
        assertEquals("100", rows.get(3).get(1));
        assertEquals("12000.00", rows.get(3).get(2));
    }

    @Test
    void textOutsideATableIsKeptAsSingleCellRows() throws Exception {
        Path pdf = pdf("misto.pdf", page -> {
            text(page, 72, 720, "Titulo do documento");
            row(page, 690, new float[] {72, 220, 360}, "A", "B", "C");
            row(page, 670, new float[] {72, 220, 360}, "1", "2", "3");
            row(page, 650, new float[] {72, 220, 360}, "4", "5", "6");
            text(page, 72, 600, "Observacao final do relatorio");
        });

        List<List<String>> rows = firstSheet(convert(pdf));

        assertEquals(List.of("Titulo do documento"), rows.get(0));
        assertEquals(List.of("A", "B", "C"), rows.get(1));
        assertEquals(List.of("Observacao final do relatorio"), rows.get(4));
    }

    @Test
    void aParagraphIsNeverTurnedIntoATable() throws Exception {
        Path pdf = pdf("paragrafo.pdf", page -> {
            text(page, 72, 700, "Este e um paragrafo comum com varias palavras seguidas");
            text(page, 72, 685, "e uma segunda linha do mesmo paragrafo de texto corrido");
            text(page, 72, 670, "e uma terceira linha que continua o mesmo assunto aqui");
        });

        List<List<String>> rows = firstSheet(convert(pdf));

        assertEquals(3, rows.size());
        for (List<String> row : rows) {
            assertEquals(1, row.size());
        }
    }

    @Test
    void eachPageBecomesItsOwnSheet() throws Exception {
        Path pdf = pdf("duas.pdf", page -> {
            row(page, 700, new float[] {72, 220, 360}, "A", "B", "C");
            row(page, 680, new float[] {72, 220, 360}, "1", "2", "3");
            row(page, 660, new float[] {72, 220, 360}, "4", "5", "6");
        }, page -> {
            row(page, 700, new float[] {72, 220, 360}, "X", "Y", "Z");
            row(page, 680, new float[] {72, 220, 360}, "7", "8", "9");
            row(page, 660, new float[] {72, 220, 360}, "0", "1", "2");
        });

        Path output = convert(pdf);

        assertEquals(List.of("Página 1", "Página 2"), sheetNames(output));
        assertEquals(List.of("A", "B", "C"), sheet(output, 2 - 1).get(0));
        assertEquals(List.of("X", "Y", "Z"), sheet(output, 2).get(0));
    }

    @Test
    void aScannedPdfWithoutTextIsReportedClearly() throws Exception {
        Path scan = TestFiles.scannedPdf(directory, "scan.pdf", 1, 600, 800, 0.9f, false);
        SourceDocument source = registry.open(scan);

        PdfLocalException error = assertThrows(PdfLocalException.class, () -> converter.toExcel(
                List.of(PageItem.pdfPage(source.id(), 0)), registry, PageSize.A4, directory.resolve("out.xlsx"),
                SaveListener.NONE));

        assertEquals(Messages.get(Messages.CONVERT_NO_TEXT), error.getMessage());
        assertTrue(Files.notExists(directory.resolve("out.xlsx")));
    }

    @Test
    void rotatedPagesAreReadAsTheUserSeesThem() throws Exception {
        Path pdf = pdf("girada.pdf", page -> {
            row(page, 700, new float[] {72, 220, 360}, "A", "B", "C");
            row(page, 680, new float[] {72, 220, 360}, "1", "2", "3");
            row(page, 660, new float[] {72, 220, 360}, "4", "5", "6");
        });
        SourceDocument source = registry.open(pdf);
        Path output = directory.resolve("out.xlsx");

        converter.toExcel(List.of(PageItem.pdfPage(source.id(), 0).rotatedBy(90)), registry, PageSize.A4, output,
                SaveListener.NONE);

        List<List<String>> rows = sheet(output, 1);
        assertEquals(3, rows.size());
        assertEquals(3, rows.get(0).size());
    }

    @Test
    void theWorkbookIsValidAndRecognisedAsExcel() throws Exception {
        Path pdf = pdf("tabela.pdf", page -> {
            row(page, 700, new float[] {72, 220, 360}, "A", "B", "C");
            row(page, 680, new float[] {72, 220, 360}, "1", "2", "3");
            row(page, 660, new float[] {72, 220, 360}, "4", "5", "6");
        });

        Path output = convert(pdf);

        assertEquals(OfficeKind.EXCEL, new OfficeFileDetector().detect(output));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            for (String part : List.of("xl/workbook.xml", "xl/worksheets/sheet1.xml", "[Content_Types].xml")) {
                try (InputStream in = zip.getInputStream(zip.getEntry(part))) {
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                }
            }
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("pdflocal-")));
        }
    }

    @Test
    void numbersAreOnlyConvertedWhenThereIsNoDoubt() {
        assertEquals(Optional.of("1234.56"), CellValues.asNumber("1.234,56"));
        assertEquals(Optional.of("12.5"), CellValues.asNumber("12,5"));
        assertEquals(Optional.of("-3.10"), CellValues.asNumber("-3,10"));
        assertEquals(Optional.of("42"), CellValues.asNumber("42"));
        assertEquals(Optional.of("0.5"), CellValues.asNumber("0,5"));
        for (String text : List.of("00123", "123.456.789-00", "35999999999", "1.234", "1,234,567", "R$ 10,00", "12/03/2026",
                "=1+1", "1e5", "0123,45", "", "abc", "12,", "1.2.3,4")) {
            assertEquals(Optional.empty(), CellValues.asNumber(text), text);
        }
    }

    @Test
    void textIsStoredAsTextAndNeverAsAFormula() throws Exception {
        Path output = directory.resolve("formula.xlsx");

        XlsxWriter.write(output, List.of(new XlsxWriter.Sheet("S", List.of(List.of("=1+1", "+cmd|' /C calc'!A0", "<b>&\"x\"")))));

        String xml = sheetXml(output, 1);
        assertFalse(xml.contains("<f>"));
        assertEquals(3, countOccurrences(xml, "t=\"inlineStr\""));
        assertTrue(xml.contains("&lt;b&gt;&amp;&quot;x&quot;"));
    }

    @Test
    void invalidXmlCharactersAndHugeCellsAreHandled() throws Exception {
        Path output = directory.resolve("limpo.xlsx");
        String dirty = "ok\u0000\u0001￾ fim 😀";
        String huge = "x".repeat(XlsxWriter.MAX_CELL_CHARACTERS + 500);

        XlsxWriter.write(output, List.of(new XlsxWriter.Sheet("S", List.of(List.of(dirty, huge)))));

        List<List<String>> rows = sheet(output, 1);
        assertEquals("ok fim 😀", rows.get(0).get(0).replaceAll("\\s+", " ").strip());
        assertEquals(XlsxWriter.MAX_CELL_CHARACTERS, rows.get(0).get(1).length());
    }

    @Test
    void sheetNamesAreSanitisedAndUnique() {
        assertEquals("a b c d e", XlsxWriter.sanitizeSheetName("a/b\\c?d*e"));
        assertEquals("Planilha", XlsxWriter.sanitizeSheetName("[]:"));
        assertEquals(31, XlsxWriter.sanitizeSheetName("x".repeat(80)).length());
        assertEquals("A", XlsxWriter.columnName(0));
        assertEquals("Z", XlsxWriter.columnName(25));
        assertEquals("AA", XlsxWriter.columnName(26));
        assertEquals("XFD", XlsxWriter.columnName(16_383));
    }

    @Test
    void duplicateSheetNamesGetASuffix() throws Exception {
        Path output = directory.resolve("nomes.xlsx");
        List<List<String>> rows = List.of(List.of("a"));

        XlsxWriter.write(output, List.of(new XlsxWriter.Sheet("Dados", rows), new XlsxWriter.Sheet("dados", rows)));

        assertEquals(List.of("Dados", "dados (2)"), sheetNames(output));
    }

    @Test
    @Tag("office")
    void libreOfficeOpensTheGeneratedWorkbook() throws Exception {
        WindowsSystemProbe probe = new WindowsSystemProbe();
        org.junit.jupiter.api.Assumptions.assumeTrue(probe.libreOffice().isPresent());
        Path pdf = pdf("tabela.pdf", page -> {
            row(page, 700, new float[] {72, 220, 360}, "Nome", "Plano", "Cidade");
            row(page, 680, new float[] {72, 220, 360}, "Maria", "Basico", "Varginha");
            row(page, 660, new float[] {72, 220, 360}, "Joao", "Pleno", "Alfenas");
        });
        Path xlsx = convert(pdf);
        Path back = directory.resolve("volta.pdf");
        String local = System.getenv("LOCALAPPDATA");
        Path home = Files.createTempDirectory(Path.of(local, "Temp"), "px");
        try {
            new OfficeConverter(probe, home).convert(xlsx, back, SaveListener.NONE);
        } finally {
            OutputFiles.deleteTree(home);
        }

        String text = TestFiles.pageTexts(back).get(0);
        assertTrue(text.contains("Varginha") && text.contains("Alfenas"), text);

        List<List<String>> again = firstSheet(convert(back));
        assertEquals(List.of("Nome", "Plano", "Cidade"), again.get(0));
        assertEquals(List.of("Maria", "Basico", "Varginha"), again.get(1));
        assertEquals(List.of("Joao", "Pleno", "Alfenas"), again.get(2));
    }

    private Path convert(Path pdf) throws Exception {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            List<PageItem> items = new ArrayList<>();
            SourceDocument source = registry.open(pdf);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                items.add(PageItem.pdfPage(source.id(), i));
            }
            Path output = directory.resolve("saida-" + pdf.getFileName().toString().replace(".pdf", ".xlsx"));
            converter.toExcel(items, registry, PageSize.A4, output, SaveListener.NONE);
            return output;
        }
    }

    @SafeVarargs
    private Path pdf(String name, PageWriter... pages) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (PageWriter writer : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    writer.write(content);
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private Path pdf(String name, PageWriter page) throws IOException {
        return pdf(name, new PageWriter[] {page});
    }

    @FunctionalInterface
    private interface PageWriter {

        void write(PDPageContentStream content) throws IOException;
    }

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static void text(PDPageContentStream content, float x, float y, String value) throws IOException {
        content.beginText();
        content.setFont(FONT, 11);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }

    private static void row(PDPageContentStream content, float y, float[] xs, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            text(content, xs[i], y, values[i]);
        }
    }

    private static void right(PDPageContentStream content, float y, float leftX, String first, float middleRightX,
                              String middle, float lastRightX, String last) throws IOException {
        text(content, leftX, y, first);
        text(content, middleRightX - width(middle), y, middle);
        text(content, lastRightX - width(last), y, last);
    }

    private static float width(String value) throws IOException {
        return FONT.getStringWidth(value) / 1000f * 11;
    }

    private static List<List<String>> firstSheet(Path xlsx) throws IOException {
        return sheet(xlsx, 1);
    }

    private static List<String> sheetNames(Path xlsx) throws IOException {
        try (ZipFile zip = new ZipFile(xlsx.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("<sheet name=\"([^\"]*)\"").matcher(xml);
            List<String> names = new ArrayList<>();
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        }
    }

    private static String sheetXml(Path xlsx, int number) throws IOException {
        try (ZipFile zip = new ZipFile(xlsx.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("xl/worksheets/sheet" + number + ".xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static List<List<String>> sheet(Path xlsx, int number) throws IOException {
        String xml = sheetXml(xlsx, number);
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = Pattern.compile("<row r=\"(\\d+)\">(.*?)</row>", Pattern.DOTALL).matcher(xml);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(rowMatcher.group(2));
            while (cell.find()) {
                int column = columnIndex(cell.group(1));
                while (cells.size() <= column) {
                    cells.add("");
                }
                String body = cell.group(3) == null ? "" : cell.group(3);
                Matcher text = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(body);
                String value;
                if (text.find()) {
                    value = unescape(text.group(1));
                } else {
                    Matcher number2 = Pattern.compile("<v>(.*?)</v>").matcher(body);
                    value = number2.find() ? number2.group(1) : "";
                }
                cells.set(column, value);
            }
            rows.add(cells);
        }
        return rows;
    }

    private static int columnIndex(String letters) {
        int value = 0;
        for (char letter : letters.toCharArray()) {
            value = value * 26 + (letter - 'A' + 1);
        }
        return value - 1;
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + part.length())) {
            count++;
        }
        return count;
    }

    @SuppressWarnings("unused")
    private static List<String> names(Stream<Path> files) {
        return files.map(path -> path.getFileName().toString()).collect(Collectors.toList());
    }
}
