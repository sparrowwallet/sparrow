package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        GlyphFontRegistry.register(new FontAwesome5());

        FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        Parent root = transactionLoader.load();
        AppController appController = transactionLoader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        stage.setTitle("Sparrow");
        stage.setMinWidth(650);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/sparrow.png")));

        appController.initializeView();

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
