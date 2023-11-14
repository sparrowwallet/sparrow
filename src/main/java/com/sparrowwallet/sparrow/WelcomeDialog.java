package com.sparrowwallet.sparrow;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class WelcomeDialog extends Dialog<Mode> {
    public WelcomeDialog() {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader welcomeLoader = new FXMLLoader(AppServices.class.getResource("welcome.fxml"));
            dialogPane.setContent(welcomeLoader.load());
            WelcomeController welcomeController = welcomeLoader.getController();
            welcomeController.initializeView();

            dialogPane.setPrefWidth(600);
            dialogPane.setPrefHeight(520);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("welcome.css").toExternalForm());

            final ButtonType nextButtonType = new javafx.scene.control.ButtonType("Next", ButtonBar.ButtonData.OK_DONE);
            final ButtonType backButtonType = new javafx.scene.control.ButtonType("Back", ButtonBar.ButtonData.LEFT);
            final ButtonType onlineButtonType = new javafx.scene.control.ButtonType("Configure Server", ButtonBar.ButtonData.APPLY);
            final ButtonType offlineButtonType = new javafx.scene.control.ButtonType(AppServices.isConnected() ? "Done" : "Later or Offline Mode", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(nextButtonType, backButtonType, onlineButtonType, offlineButtonType);

            Button nextButton = (Button)dialogPane.lookupButton(nextButtonType);
            Button backButton = (Button)dialogPane.lookupButton(backButtonType);
            Button onlineButton = (Button)dialogPane.lookupButton(onlineButtonType);
            Button offlineButton = (Button)dialogPane.lookupButton(offlineButtonType);

            nextButton.managedProperty().bind(nextButton.visibleProperty());
            backButton.managedProperty().bind(backButton.visibleProperty());
            onlineButton.managedProperty().bind(onlineButton.visibleProperty());
            offlineButton.managedProperty().bind(offlineButton.visibleProperty());

            backButton.setDisable(true);
            onlineButton.visibleProperty().bind(nextButton.visibleProperty().not());
            offlineButton.visibleProperty().bind(nextButton.visibleProperty().not());

            nextButton.addEventFilter(ActionEvent.ACTION, event -> {
                if(!welcomeController.next()) {
                    nextButton.setVisible(false);
                    onlineButton.setDefaultButton(true);
                }
                backButton.setDisable(false);
                event.consume();
            });

            backButton.addEventFilter(ActionEvent.ACTION, event -> {
                nextButton.setVisible(true);
                if(!welcomeController.back()) {
                    backButton.setDisable(true);
                }
                event.consume();
            });

            setResultConverter(dialogButton -> dialogButton == onlineButtonType ? Mode.ONLINE : Mode.OFFLINE);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
