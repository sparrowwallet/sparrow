package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

public class PreferencesController implements Initializable {
    private Config config;

    @FXML
    private ToggleGroup preferencesMenu;

    @FXML
    private StackPane preferencesPane;

    private final BooleanProperty closing = new SimpleBooleanProperty(false);

    private final BooleanProperty reconnectOnClosing = new SimpleBooleanProperty(false);

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public Config getConfig() {
        return config;
    }

    public void initializeView(Config config) {
        this.config = config;
        preferencesMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            if(selectedToggle == null) {
                oldValue.setSelected(true);
                return;
            }

            PreferenceGroup preferenceGroup = (PreferenceGroup) selectedToggle.getUserData();
            String fxmlName = preferenceGroup.toString().toLowerCase(Locale.ROOT);
            setPreferencePane(fxmlName);
        });
    }

    public void selectGroup(PreferenceGroup preferenceGroup) {
        for(Toggle toggle : preferencesMenu.getToggles()) {
            if(toggle.getUserData().equals(preferenceGroup)) {
                Platform.runLater(() -> preferencesMenu.selectToggle(toggle));
                return;
            }
        }
    }

    BooleanProperty closingProperty() {
        return closing;
    }

    public boolean isReconnectOnClosing() {
        return reconnectOnClosing.get();
    }

    public BooleanProperty reconnectOnClosingProperty() {
        return reconnectOnClosing;
    }

    FXMLLoader setPreferencePane(String fxmlName) {
        preferencesPane.getChildren().removeAll(preferencesPane.getChildren());

        try {
            FXMLLoader preferencesDetailLoader = new FXMLLoader(AppServices.class.getResource("preferences/" + fxmlName + ".fxml"));
            Node preferenceGroupNode = preferencesDetailLoader.load();
            PreferencesDetailController controller = preferencesDetailLoader.getController();
            controller.setMasterController(this);
            controller.initializeView(config);
            preferencesPane.getChildren().add(preferenceGroupNode);

            return preferencesDetailLoader;
        } catch (IOException e) {
            throw new IllegalStateException("Can't find pane", e);
        }
    }
}
