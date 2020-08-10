package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.Mode;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.HyperlinkLabel;
import org.controlsfx.control.StatusBar;
import org.controlsfx.control.ToggleSwitch;

public class WelcomeDialog extends Dialog<Mode> {
    private static final String[] ELECTRUM_SERVERS = new String[]{
            "ElectrumX (Recommended)", "https://github.com/spesmilo/electrumx",
            "electrs", "https://github.com/romanz/electrs",
            "esplora-electrs", "https://github.com/Blockstream/electrs"};

    private final HostServices hostServices;

    public WelcomeDialog(HostServices services) {
        this.hostServices = services;

        final DialogPane dialogPane = getDialogPane();

        setTitle("Welcome to Sparrow");
        dialogPane.setHeaderText("Welcome to Sparrow!");
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        dialogPane.setPrefWidth(600);
        dialogPane.setPrefHeight(450);

        Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setSmooth(false);
            imageView.setImage(image);
            dialogPane.setGraphic(imageView);
        }

        final ButtonType onlineButtonType = new javafx.scene.control.ButtonType("Configure Server", ButtonBar.ButtonData.OK_DONE);
        final ButtonType offlineButtonType = new javafx.scene.control.ButtonType("Later or Offline Mode", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(onlineButtonType, offlineButtonType);

        final VBox content = new VBox(20);
        content.setPadding(new Insets(20, 20, 20, 20));
        content.getChildren().add(createParagraph("Sparrow can operate in both an online and offline mode. In the online mode it connects to your Electrum server to display transaction history. In the offline mode it is useful as a transaction editor and as an airgapped multisig coordinator."));
        content.getChildren().add(createParagraph("For privacy and security reasons it is not recommended to use a public Electrum server. Install an Electrum server that connects to your full node to index the blockchain and provide full privacy. Examples include:"));

        VBox linkBox = new VBox();
        for(int i = 0; i < ELECTRUM_SERVERS.length; i+=2) {
            linkBox.getChildren().add(createBulletedLink(ELECTRUM_SERVERS[i], ELECTRUM_SERVERS[i+1]));
        }
        content.getChildren().add(linkBox);

        content.getChildren().add(createParagraph("You can change your mode at any time using the toggle in the status bar:"));
        content.getChildren().add(createStatusBar(onlineButtonType, offlineButtonType));

        dialogPane.setContent(content);

        setResultConverter(dialogButton -> dialogButton == onlineButtonType ? Mode.ONLINE : Mode.OFFLINE);
    }

    private Label createParagraph(String text) {
        Label label = new Label(text);
        label.setWrapText(true);

        return label;
    }

    private HyperlinkLabel createBulletedLink(String name, String url) {
        HyperlinkLabel label = new HyperlinkLabel(" \u2022 [" + name + "]");
        label.setOnAction(event -> {
            hostServices.showDocument(url);
        });

        return label;
    }

    private StatusBar createStatusBar(ButtonType onlineButtonType, ButtonType offlineButtonType) {
        StatusBar statusBar = new StatusBar();
        statusBar.setText("Online Mode");
        statusBar.getRightItems().add(createToggle(statusBar, onlineButtonType, offlineButtonType));

        return statusBar;
    }

    private ToggleSwitch createToggle(StatusBar statusBar, ButtonType onlineButtonType, ButtonType offlineButtonType) {
        ToggleSwitch toggleSwitch = new UnlabeledToggleSwitch();
        toggleSwitch.selectedProperty().addListener((observable, oldValue, newValue) -> {
            Button onlineButton = (Button) getDialogPane().lookupButton(onlineButtonType);
            onlineButton.setDefaultButton(newValue);
            Button offlineButton = (Button) getDialogPane().lookupButton(offlineButtonType);
            offlineButton.setDefaultButton(!newValue);
            statusBar.setText(newValue ? "Online Mode" : "Offline Mode");
        });

        toggleSwitch.setSelected(true);
        return toggleSwitch;
    }
}
