package br.com.pdflocal.app;

import javafx.application.Application;

public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(PdfLocalApp.class, args);
    }
}
