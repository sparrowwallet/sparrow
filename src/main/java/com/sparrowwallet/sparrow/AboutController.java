package com.sparrowwallet.sparrow;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class AboutController {
    private Stage stage;

    @FXML
    private Label title;

    public void initializeView() {
        title.setText(SparrowWallet.APP_NAME + " " + SparrowWallet.APP_VERSION + SparrowWallet.APP_VERSION_SUFFIX);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void close(ActionEvent event) {
        stage.close();
    }

    public void openDonate(ActionEvent event) {
        AppServices.get().getApplication().getHostServices().showDocument("https://sparrowwallet.com/donate");
    }
}
