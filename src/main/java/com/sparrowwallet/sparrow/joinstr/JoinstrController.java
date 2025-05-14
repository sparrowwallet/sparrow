package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.AppServices;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class JoinstrController extends JoinstrFormController {

    private Stage stage;

    @FXML
    private StackPane joinstrPane;

    @FXML
    private VBox joinstrMenuBox;

    @FXML
    private ToggleGroup joinstrMenu;


    public void initializeView() {
        joinstrMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            if(selectedToggle == null) {
                oldValue.setSelected(true);
                return;
            }

            JoinstrDisplay display = (JoinstrDisplay)selectedToggle.getUserData();

            boolean existing = false;
            for(Node joinstrDisplay : joinstrPane.getChildren()) {
                if(joinstrDisplay.getUserData().equals(display)) {
                    existing = true;
                    joinstrDisplay.setViewOrder(0);
                } else if(display != JoinstrDisplay.LOCK) {
                    joinstrDisplay.setViewOrder(1);
                }
            }

            try {
                if(!existing) {
                    URL url = AppServices.class.getResource("joinstr/" + display.toString().toLowerCase(Locale.ROOT) + ".fxml");
                    if(url == null) {
                        throw new IllegalStateException("Cannot find joinstr/" + display.toString().toLowerCase(Locale.ROOT) + ".fxml");
                    }

                    FXMLLoader displayLoader = new FXMLLoader(url);
                    Node joinstrDisplay = displayLoader.load();

                    joinstrDisplay.setUserData(display);
                    joinstrDisplay.setViewOrder(1);

                    JoinstrFormController controller = displayLoader.getController();
                    JoinstrForm joinstrForm = getJoinstrForm();
                    controller.setJoinstrForm(joinstrForm);

                    joinstrPane.getChildren().add(joinstrDisplay);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't find pane", e);
            }

        });

        for(Toggle toggle : joinstrMenu.getToggles()) {
            ToggleButton toggleButton = (ToggleButton) toggle;
            toggleButton.managedProperty().bind(toggleButton.visibleProperty());
        }

        joinstrMenuBox.managedProperty().bind(joinstrMenuBox.visibleProperty());
        joinstrMenuBox.visibleProperty().bind(getJoinstrForm().lockedProperty().not());

    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void close(ActionEvent event) {
        stage.close();
    }

}
