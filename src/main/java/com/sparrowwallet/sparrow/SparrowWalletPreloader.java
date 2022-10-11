package com.sparrowwallet.sparrow;

import javafx.application.Preloader;
import javafx.stage.Stage;

public class SparrowWalletPreloader extends Preloader {
    @Override
    public void start(Stage stage) {
        com.sun.glass.ui.Application.GetApplication().setName("Sparrow");
    }
}
