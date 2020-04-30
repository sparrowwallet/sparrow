package com.sparrowwallet.sparrow.control;

import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.KeystoreFileImport;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;

import java.io.*;

public class KeystoreFileImportPane extends TitledPane {
    private final KeystoreImportAccordion importAccordion;
    private final Wallet wallet;
    private final KeystoreFileImport importer;

    private Label mainLabel;
    private Label descriptionLabel;
    private Hyperlink showHideLink;
    private Button importButton;

    private final SimpleStringProperty password = new SimpleStringProperty("");

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

        HBox descriptionBox = new HBox();
        descriptionBox.setSpacing(7);
        labelsBox.getChildren().add(descriptionBox);

        descriptionLabel = new Label("Keystore file import");
        descriptionLabel.getStyleClass().add("description-label");
        showHideLink = new Hyperlink("View Details...");
        showHideLink.managedProperty().bind(showHideLink.visibleProperty());
        showHideLink.setOnAction(event -> {
            if(showHideLink.getText().contains("View")) {
                setExpanded(true);
                showHideLink.setText("Hide Details...");
            } else {
                setExpanded(false);
                showHideLink.setText("View Details...");
            }
        });
        descriptionBox.getChildren().addAll(descriptionLabel, showHideLink);

        listItem.getChildren().add(labelsBox);
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        importButton = new Button("Import File...");
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
            importFile(file, null);
        }
    }

    private void importFile(File file, String password) {
        if(file.exists()) {
            try {
                if(importer.isEncrypted(file) && password == null) {
                    descriptionLabel.getStyleClass().remove("description-error");
                    descriptionLabel.getStyleClass().add("description-label");
                    descriptionLabel.setText("Password Required");
                    showHideLink.setVisible(false);
                    setContent(getPasswordEntry(file));
                    importButton.setDisable(true);
                    setExpanded(true);
                } else {
                    InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                    Keystore keystore = importer.getKeystore(wallet.getScriptType(), inputStream, password);
                    EventManager.get().post(new KeystoreImportEvent(keystore));
                }
            } catch (Exception e) {
                descriptionLabel.getStyleClass().remove("description-label");
                descriptionLabel.getStyleClass().add("description-error");
                descriptionLabel.setText("Import Error");
                String errorMessage = e.getMessage();
                if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                    errorMessage = e.getCause().getMessage();
                }
                if(e instanceof ECKey.InvalidPasswordException || e.getCause() instanceof ECKey.InvalidPasswordException) {
                    errorMessage = "Invalid wallet password";
                }
                if(e instanceof JsonParseException || e.getCause() instanceof JsonParseException) {
                    errorMessage = "File was not in JSON format";
                }
                setContent(getContentBox(errorMessage));
                setExpanded(true);
                showHideLink.setText("Hide Details...");
                importButton.setDisable(false);
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

    private Node getPasswordEntry(File file) {
        CustomPasswordField passwordField = (CustomPasswordField) TextFields.createClearablePasswordField();
        passwordField.setPromptText("Wallet password");
        password.bind(passwordField.textProperty());
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Button importEncryptedButton = new Button("Import");
        importEncryptedButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            importFile(file, password.get());
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(passwordField);
        contentBox.getChildren().add(importEncryptedButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
