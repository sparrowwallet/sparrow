package com.sparrowwallet.sparrow;

import javafx.event.ActionEvent;
import javafx.stage.Stage;

public class AboutController {
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void close(ActionEvent event) {
        stage.close();
    }
}
