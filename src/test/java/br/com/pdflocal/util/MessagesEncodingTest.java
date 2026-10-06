package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessagesEncodingTest {

    @Test
    void messagesFileIsValidUtf8() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/messages.properties")) {
            assertNotNull(in);
            bytes = in.readAllBytes();
        }

        String text = null;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError("messages.properties must be saved as UTF-8: " + e.getMessage(), e);
        }

        assertFalse(text.contains("�"));
        assertFalse(text.contains("Ã"));
    }

    @Test
    void accentedMessagesAreReadCorrectly() {
        assertEquals("Não foi possível ler o arquivo", Messages.get(Messages.FILE_UNREADABLE));
        assertEquals("Versão 1.0.0", Messages.format("app.about.version", "1.0.0"));
    }
}
