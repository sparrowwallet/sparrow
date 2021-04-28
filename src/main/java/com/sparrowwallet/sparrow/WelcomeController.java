package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.StatusBar;

public class WelcomeController {
    @FXML
    private VBox welcomeBox;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private VBox step4;

    @FXML
    private StatusBar serverStatus;

    @FXML
    private UnlabeledToggleSwitch serverToggle;

    public void initializeView() {
        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());
        step4.managedProperty().bind(step4.visibleProperty());

        step2.setVisible(false);
        step3.setVisible(false);
        step4.setVisible(false);

        welcomeBox.getStyleClass().add("offline");
        serverStatus.setText("Offline");
        serverToggle.addEventFilter(MouseEvent.MOUSE_RELEASED, Event::consume);
        Tooltip tooltip = new Tooltip("Demonstration only - you are not connected!");
        tooltip.setShowDelay(Duration.ZERO);
        serverToggle.setTooltip(tooltip);
        serverToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            serverStatus.setText(newValue ? "Connected (demonstration only)" : "Offline");
        });
    }

    public boolean next() {
        if(step1.isVisible()) {
            step1.setVisible(false);
            step2.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("public-electrum");
            PauseTransition wait = new PauseTransition(Duration.millis(200));
            wait.setOnFinished((e) -> {
                serverToggle.setSelected(true);
                serverStatus.setText("Connected to a Public Server (demonstration only)");
            });
            wait.play();
            return true;
        }

        if(step2.isVisible()) {
            step2.setVisible(false);
            step3.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("bitcoin-core");
            serverToggle.setSelected(true);
            serverStatus.setText("Connected to Bitcoin Core (demonstration only)");
            return true;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step4.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("private-electrum");
            serverToggle.setSelected(true);
            serverStatus.setText("Connected to a Private Electrum Server (demonstration only)");
        }

        return false;
    }

    public boolean back() {
        if(step2.isVisible()) {
            step2.setVisible(false);
            step1.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("offline");
            PauseTransition wait = new PauseTransition(Duration.millis(200));
            wait.setOnFinished((e) -> {
                serverToggle.setSelected(false);
                serverStatus.setText("Offline");
            });
            wait.play();
            return false;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step2.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("public-electrum");
            serverToggle.setSelected(true);
            serverStatus.setText("Connected to a Public Server (demonstration only)");
            return true;
        }

        if(step4.isVisible()) {
            step4.setVisible(false);
            step3.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("bitcoin-core");
            serverToggle.setSelected(true);
            serverStatus.setText("Connected to Bitcoin Core (demonstration only)");
            return true;
        }

        return false;
    }
}
