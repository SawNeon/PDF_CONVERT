package br.com.pdflocal.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlPolicy {

    private static final int SEARCH_LIMIT = 65_536;
    private static final Pattern HEAD = Pattern.compile("<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML = Pattern.compile("<html(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTYPE = Pattern.compile("<!doctype[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final String POLICY = "script-src 'none'; connect-src 'none'; object-src 'none'; "
            + "frame-src 'none'; form-action 'none'";

    private HtmlPolicy() {
    }

    static byte[] apply(byte[] html, URI baseDirectory) {
        String text = new String(html, StandardCharsets.ISO_8859_1);
        String tags = "<meta http-equiv=\"Content-Security-Policy\" content=\"" + POLICY + "\">"
                + "<base href=\"" + escape(baseDirectory.toString()) + "\">";
        String window = text.substring(0, Math.min(text.length(), SEARCH_LIMIT));
        int position;
        String insertion = tags;
        Matcher head = HEAD.matcher(window);
        if (head.find()) {
            position = head.end();
        } else {
            Matcher root = HTML.matcher(window);
            if (root.find()) {
                position = root.end();
                insertion = "<head>" + tags + "</head>";
            } else {
                Matcher doctype = DOCTYPE.matcher(window);
                position = doctype.find() ? doctype.end() : 0;
                insertion = "<head>" + tags + "</head>";
            }
        }
        String result = text.substring(0, position) + insertion + text.substring(position);
        return result.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
