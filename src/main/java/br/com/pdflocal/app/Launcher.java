package br.com.pdflocal.app;

import javafx.application.Application;

/**
 * Ponto de entrada. Não estende {@link Application} de propósito: assim o app
 * sobe também fora do module path (jar simples e jpackage).
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(PdfLocalApp.class, args);
    }
}
