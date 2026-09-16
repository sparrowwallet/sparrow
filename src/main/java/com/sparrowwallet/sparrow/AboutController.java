package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.control.DialogImage;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class AboutController {
    private Stage stage;

    @FXML
    private Label title;

    @FXML
    private DialogImage dialogImage;

    public void initializeView() {
        title.setText(SparrowWallet.APP_NAME + " " + SparrowWallet.APP_VERSION + SparrowWallet.APP_VERSION_SUFFIX);
    }

    public void refreshTheme() {
        String darkCss = AppServices.class.getResource("darktheme.css").toExternalForm();
        if(AppServices.isDarkTheme()) {
            if(!stage.getScene().getStylesheets().contains(darkCss)) {
                stage.getScene().getStylesheets().add(darkCss);
            }
        } else {
            stage.getScene().getStylesheets().remove(darkCss);
        }

        dialogImage.refresh();
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
