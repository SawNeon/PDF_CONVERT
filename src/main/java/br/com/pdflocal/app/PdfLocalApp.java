package br.com.pdflocal.app;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class PdfLocalApp extends Application {

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Scene scene = new Scene(new StackPane(), 1100, 700);
        stage.setTitle("PdfLocal");
        stage.setScene(scene);
        stage.show();
    }
}
