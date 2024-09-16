package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.event.LanguageChangedInWelcomeEvent;
import com.sparrowwallet.sparrow.i18n.LanguagesManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;

public class WelcomeDialog extends Dialog<Mode> {

    private WelcomeController welcomeController;

    public WelcomeDialog(boolean isFirstExecution) {
        super();
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        EventManager.get().register(this);

        try {
            FXMLLoader welcomeLoader = new FXMLLoader(AppServices.class.getResource("welcome.fxml"), LanguagesManager.getResourceBundle());
            dialogPane.setContent(welcomeLoader.load());
            welcomeController = welcomeLoader.getController();
            welcomeController.initializeView(isFirstExecution);

            dialogPane.setPrefWidth(600);
            dialogPane.setPrefHeight(520);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("welcome.css").toExternalForm());

            final ButtonType nextButtonType = new javafx.scene.control.ButtonType(LanguagesManager.getMessage("welcome.next"), ButtonBar.ButtonData.OK_DONE);
            final ButtonType backButtonType = new javafx.scene.control.ButtonType(LanguagesManager.getMessage("welcome.back"), ButtonBar.ButtonData.LEFT);
            final ButtonType onlineButtonType = new javafx.scene.control.ButtonType(LanguagesManager.getMessage("welcome.configure-server"), ButtonBar.ButtonData.APPLY);
            final ButtonType offlineButtonType = new javafx.scene.control.ButtonType(AppServices.isConnected() ? LanguagesManager.getMessage("welcome.done") : LanguagesManager.getMessage("welcome.configure-later"), ButtonBar.ButtonData.CANCEL_CLOSE);
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

    @Subscribe
    public void languageChanged(LanguageChangedInWelcomeEvent event) {
        final DialogPane dialogPane = getDialogPane();

        try {
            FXMLLoader welcomeLoader = new FXMLLoader(AppServices.class.getResource("welcome.fxml"), LanguagesManager.getResourceBundle());
            dialogPane.setContent(welcomeLoader.load());
            welcomeController = welcomeLoader.getController();
            welcomeController.initializeView(true);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
