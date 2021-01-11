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
        title.setText(MainApp.APP_NAME + " " + MainApp.APP_VERSION);
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
