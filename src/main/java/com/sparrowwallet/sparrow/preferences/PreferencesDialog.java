package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.RequestConnectEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class PreferencesDialog extends Dialog<Boolean> {
    public PreferencesDialog() {
        this(null);
    }

    public PreferencesDialog(PreferenceGroup initialGroup) {
        this(initialGroup, false);
    }

    public PreferencesDialog(PreferenceGroup initialGroup, boolean initialSetup) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        try {
            FXMLLoader preferencesLoader = new FXMLLoader(AppServices.class.getResource("preferences/preferences.fxml"));
            dialogPane.setContent(Borders.wrap(preferencesLoader.load()).emptyBorder().buildAll());
            PreferencesController preferencesController = preferencesLoader.getController();
            preferencesController.initializeView(Config.get());
            if(initialGroup != null) {
                preferencesController.selectGroup(initialGroup);
            } else {
                preferencesController.selectGroup(PreferenceGroup.GENERAL);
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

            preferencesController.reconnectOnClosingProperty().set(AppServices.isConnecting() || AppServices.isConnected());
            setOnCloseRequest(event -> {
                preferencesController.closingProperty().set(true);
                if(preferencesController.isReconnectOnClosing() && !(AppServices.isConnecting() || AppServices.isConnected())) {
                    EventManager.get().post(new RequestConnectEvent());
                }
            });

            setResultConverter(dialogButton -> dialogButton == newWalletButtonType ? Boolean.TRUE : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
