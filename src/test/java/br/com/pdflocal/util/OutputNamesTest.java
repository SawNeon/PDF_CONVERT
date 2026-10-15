package br.com.pdflocal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OutputNamesTest {

    @Test
    void replacesTheExtensionAndAppendsTheSuffix() {
        assertEquals("laudo_comprimido.pdf", OutputNames.suggest("laudo.pdf", "_comprimido"));
        assertEquals("foto_editado.pdf", OutputNames.suggest("foto.JPG", "_editado"));
    }

    @Test
    void onlyTheLastExtensionIsRemoved() {
        assertEquals("contrato.v2_unido.pdf", OutputNames.suggest("contrato.v2.pdf", "_unido"));
    }

    @Test
    void namesWithoutExtensionAreKept() {
        assertEquals("anexo_unido.pdf", OutputNames.suggest("anexo", "_unido"));
    }

    @Test
    void fallsBackWhenThereIsNoUsableName() {
        assertEquals("documento_unido.pdf", OutputNames.suggest(null, "_unido"));
        assertEquals("documento_unido.pdf", OutputNames.suggest("", "_unido"));
        assertEquals("documento_unido.pdf", OutputNames.suggest(".pdf", "_unido"));
    }

    @Test
    void charactersInvalidOnWindowsAreDropped() {
        assertEquals("ab_unido.pdf", OutputNames.suggest("a:b?.pdf", "_unido"));
    }
}
