package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.List;

public class SeedEntryDialog extends Dialog<List<String>> {
    private final MnemonicKeystoreEntryPane keystorePane;

    public SeedEntryDialog(String name, int numWords) {
        final DialogPane dialogPane = new MnemonicGridDialogPane();
        setDialogPane(dialogPane);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        int lines = numWords / 3;
        int height = lines * 40;

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("edge-to-edge");
        scrollPane.setPrefHeight(104 + height);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        Accordion keystoreAccordion = new Accordion();
        scrollPane.setContent(keystoreAccordion);

        keystorePane = new MnemonicKeystoreEntryPane(name, numWords);
        keystorePane.setAnimated(false);
        keystoreAccordion.getPanes().add(keystorePane);

        stackPane.getChildren().addAll(anchorPane);

        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button)dialogPane.lookupButton(ButtonType.OK);
        okButton.disableProperty().bind(keystorePane.validProperty().not());

        final ButtonType generateButtonType = new javafx.scene.control.ButtonType("Generate New", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().add(generateButtonType);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? keystorePane.wordEntriesProperty.get() : null;
        });

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(180 + height);
        AppServices.moveToActiveWindowScreen(this);

        Platform.runLater(() -> keystoreAccordion.setExpandedPane(keystorePane));
    }

    private class MnemonicGridDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button generateButton = new Button(buttonType.getText());
                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(generateButton, buttonData);
                generateButton.setOnAction(event -> {
                    keystorePane.generateNew();
                });

                button = generateButton;
            } else {
                button = super.createButton(buttonType);
            }

            return button;
        }
    }

    public boolean isGenerated() {
        return keystorePane.isGenerated();
    }
}
