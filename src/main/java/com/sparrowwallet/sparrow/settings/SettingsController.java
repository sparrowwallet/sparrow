package com.sparrowwallet.sparrow.settings;

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

public class SettingsController implements Initializable {
    private Config config;

    @FXML
    private ToggleGroup settingsMenu;

    @FXML
    private StackPane settingsPane;

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
        settingsMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            if(selectedToggle == null) {
                oldValue.setSelected(true);
                return;
            }

            SettingsGroup settingsGroup = (SettingsGroup) selectedToggle.getUserData();
            String fxmlName = settingsGroup.toString().toLowerCase(Locale.ROOT);
            setPreferencePane(fxmlName);
        });
    }

    public void selectGroup(SettingsGroup settingsGroup) {
        for(Toggle toggle : settingsMenu.getToggles()) {
            if(toggle.getUserData().equals(settingsGroup)) {
                Platform.runLater(() -> settingsMenu.selectToggle(toggle));
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
        settingsPane.getChildren().removeAll(settingsPane.getChildren());

        try {
            FXMLLoader settingsDetailLoader = new FXMLLoader(AppServices.class.getResource("settings/" + fxmlName + ".fxml"));
            Node preferenceGroupNode = settingsDetailLoader.load();
            SettingsDetailController controller = settingsDetailLoader.getController();
            controller.setMasterController(this);
            controller.initializeView(config);
            settingsPane.getChildren().add(preferenceGroupNode);

            return settingsDetailLoader;
        } catch (IOException e) {
            throw new IllegalStateException("Can't find pane", e);
        }
    }
}
