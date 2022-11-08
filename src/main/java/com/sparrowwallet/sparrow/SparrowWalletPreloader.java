package com.sparrowwallet.sparrow;

import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SparrowWalletPreloader extends Preloader {

    private Stage preloaderStage;
    private Scene splashscreen;
    public static final int SPLASHSCREEN_TIME_DELAY_MS = 2_000;

    @Override
    public void init() throws Exception {
        FXMLLoader splashLoader = new FXMLLoader(AppServices.class.getResource("splashscreen.fxml"));
        splashscreen = new Scene(splashLoader.load());
    }

    @Override
    public void start(Stage stage) {
        com.sun.glass.ui.Application.GetApplication().setName("Sparrow");

        preloaderStage = stage;
        preloaderStage.setScene(splashscreen);
        preloaderStage.initStyle(StageStyle.UNDECORATED);
        preloaderStage.show();

    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification info) {
        if (info.getType() == StateChangeNotification.Type.BEFORE_START) {

            // put JavaFX-Application thread to sleep so that splashscreen gets the ability to show. Minimize or
            // delete thread sleep time if time intensive work is done in the SparrowDesktop.init() method
            try {
                Thread.sleep(SPLASHSCREEN_TIME_DELAY_MS);
            } catch (InterruptedException e) {
                // Not critical if splashscreen is hidden to early
            }
            preloaderStage.hide();
        }
    }
}
