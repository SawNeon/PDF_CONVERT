package br.com.pdflocal.app;

import br.com.pdflocal.ui.MainView;
import br.com.pdflocal.ui.Theme;
import br.com.pdflocal.util.AppSettings;
import br.com.pdflocal.util.LogSetup;
import br.com.pdflocal.util.Messages;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Handler;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PdfLocalApp extends Application {

    private static final double INITIAL_WIDTH = 1100;
    private static final double INITIAL_HEIGHT = 720;
    private static final double MIN_WIDTH = 720;
    private static final double MIN_HEIGHT = 480;

    private MainView view;
    private Handler logHandler;

    @Override
    public void start(Stage stage) {
        logHandler = LogSetup.install(LogSetup.defaultFolder());
        AppSettings settings = AppSettings.defaults();
        Application.setUserAgentStylesheet(Theme.parse(settings.theme()).userAgentStylesheet());

        view = new MainView(stage, settings);
        Scene scene = new Scene(view, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/pdflocal.css")).toExternalForm());

        stage.setTitle(Messages.get("app.title"));
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();

        List<Path> startupFiles = startupFiles();
        if (!startupFiles.isEmpty()) {
            view.importFiles(startupFiles);
        }
    }

    @Override
    public void stop() {
        if (view != null) {
            view.close();
        }
        LogSetup.uninstall(logHandler);
    }

    private List<Path> startupFiles() {
        return getParameters().getUnnamed().stream()
                .map(PdfLocalApp::toPath)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Path> toPath(String argument) {
        try {
            return Optional.of(Path.of(argument));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
