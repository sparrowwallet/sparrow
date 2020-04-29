package com.sparrowwallet.sparrow.control;

import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.external.KeystoreFileImport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.HyperlinkLabel;

import java.io.*;

public class KeystoreFileImportPane extends TitledPane {
    private final KeystoreImportAccordion importAccordion;
    private final Wallet wallet;
    private final KeystoreFileImport importer;

    private Label mainLabel;
    private HyperlinkLabel descriptionLabel;

    public KeystoreFileImportPane(KeystoreImportAccordion importAccordion, Wallet wallet, KeystoreFileImport importer) {
        this.importAccordion = importAccordion;
        this.wallet = wallet;
        this.importer = importer;

        setPadding(Insets.EMPTY);

        setGraphic(getTitle());
        getStyleClass().add("importpane");
        setContent(getContentBox(importer.getKeystoreImportDescription()));

        removeArrow();
    }

    private void removeArrow() {
        Platform.runLater(() -> {
            Node arrow = this.lookup(".arrow");
            if (arrow != null) {
                arrow.setVisible(false);
                arrow.setManaged(false);
            } else {
                removeArrow();
            }
        });
    }

    private Node getTitle() {
        HBox listItem = new HBox();
        listItem.setPadding(new Insets(10, 20, 10, 10));
        listItem.setSpacing(10);

        HBox imageBox = new HBox();
        imageBox.setMinWidth(50);
        imageBox.setMinHeight(50);
        listItem.getChildren().add(imageBox);

        Image image = new Image("image/" + importer.getWalletModel().getType() + ".png", 50, 50, true, true);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setImage(image);
            imageBox.getChildren().add(imageView);
        }

        VBox labelsBox = new VBox();
        labelsBox.setSpacing(5);
        labelsBox.setAlignment(Pos.CENTER_LEFT);
        this.mainLabel = new Label();
        mainLabel.setText(importer.getName());
        mainLabel.getStyleClass().add("main-label");
        labelsBox.getChildren().add(mainLabel);

        this.descriptionLabel = new HyperlinkLabel();

        labelsBox.getChildren().add(descriptionLabel);
        descriptionLabel.getStyleClass().add("description-label");
        descriptionLabel.setText("Keystore file import [View Details...]");
        descriptionLabel.setOnAction(event -> {
            setExpanded(true);
        });
        listItem.getChildren().add(labelsBox);
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button importButton = new Button("Import File...");
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setOnAction(event -> {
            importFile();
        });

        buttonBox.getChildren().add(importButton);
        listItem.getChildren().add(buttonBox);

        this.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
            listItem.setPrefWidth(newValue.getWidth());
        });

        return listItem;
    }

    private void importFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open " + importer.getWalletModel().toDisplayString() + " keystore");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("JSON", "*.json")
        );

        File file = fileChooser.showOpenDialog(window);
        if(file != null) {
            importFile(file);
        }
    }

    private void importFile(File file) {
        if(file.exists()) {
            try {
                InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                Keystore keystore = importer.getKeystore(wallet.getScriptType(), inputStream);
                EventManager.get().post(new KeystoreImportEvent(keystore));
            } catch (Exception e) {
                setExpanded(false);
                descriptionLabel.getStyleClass().remove("description-label");
                descriptionLabel.getStyleClass().add("description-error");
                descriptionLabel.setText("Error Importing [View Details...]");
                String errorMessage = e.getMessage();
                if(e.getCause() != null) {
                    errorMessage = e.getCause().getMessage();
                }
                if(e instanceof JsonParseException || e.getCause() instanceof JsonParseException) {
                    errorMessage = "File was not in JSON format";
                }
                setContent(getContentBox(errorMessage));
            }
        }
    }

    private Node getContentBox(String message) {
        Label details = new Label(message);
        details.setWrapText(true);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.getChildren().add(details);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        double width = TextUtils.computeTextWidth(details.getFont(), message, 0.0D);
        double numLines = Math.max(1, width / 400);
        double height = Math.max(60, numLines * 40);
        contentBox.setPrefHeight(height);

        return contentBox;
    }
}
