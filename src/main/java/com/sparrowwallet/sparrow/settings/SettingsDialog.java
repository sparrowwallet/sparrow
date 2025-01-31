package com.sparrowwallet.sparrow.settings;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.RequestConnectEvent;
import com.sparrowwallet.sparrow.io.Config;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class SettingsDialog extends Dialog<Boolean> {
    public SettingsDialog() {
        this(null);
    }

    public SettingsDialog(SettingsGroup initialGroup) {
        this(initialGroup, false);
    }

    public SettingsDialog(SettingsGroup initialGroup, boolean initialSetup) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        try {
            FXMLLoader settingsLoader = new FXMLLoader(AppServices.class.getResource("settings/settings.fxml"));
            dialogPane.setContent(Borders.wrap(settingsLoader.load()).emptyBorder().buildAll());
            SettingsController settingsController = settingsLoader.getController();
            settingsController.initializeView(Config.get());
            if(initialGroup != null) {
                settingsController.selectGroup(initialGroup);
            } else {
                settingsController.selectGroup(SettingsGroup.GENERAL);
            }

            final ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(closeButtonType);

            final ButtonType newWalletButtonType = new javafx.scene.control.ButtonType("Create New Wallet", ButtonBar.ButtonData.OK_DONE);
            if(initialSetup) {
                dialogPane.getButtonTypes().addAll(newWalletButtonType);
            }

            dialogPane.setPrefWidth(750);
            dialogPane.setPrefHeight(630);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            settingsController.reconnectOnClosingProperty().set(AppServices.isConnecting() || AppServices.isConnected());
            setOnCloseRequest(event -> {
                settingsController.closingProperty().set(true);
                if(settingsController.isReconnectOnClosing() && !(AppServices.isConnecting() || AppServices.isConnected())) {
                    EventManager.get().post(new RequestConnectEvent());
                }
            });

            setResultConverter(dialogButton -> dialogButton == newWalletButtonType ? Boolean.TRUE : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
