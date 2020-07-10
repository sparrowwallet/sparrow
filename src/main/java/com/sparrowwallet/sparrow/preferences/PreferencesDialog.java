package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.io.Config;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class PreferencesDialog extends Dialog<Void> {
    public PreferencesDialog() {
        this(null);
    }

    public PreferencesDialog(PreferenceGroup initialGroup) {
        final DialogPane dialogPane = getDialogPane();

        try {
            FXMLLoader preferencesLoader = new FXMLLoader(AppController.class.getResource("preferences/preferences.fxml"));
            dialogPane.setContent(Borders.wrap(preferencesLoader.load()).lineBorder().outerPadding(0).innerPadding(0).buildAll());
            PreferencesController preferencesController = preferencesLoader.getController();
            preferencesController.initializeView(Config.get());
            if(initialGroup != null) {
                preferencesController.selectGroup(initialGroup);
            } else {
                preferencesController.selectGroup(PreferenceGroup.GENERAL);
            }

            final ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(closeButtonType);
            dialogPane.setPrefWidth(650);
            dialogPane.setPrefHeight(500);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
