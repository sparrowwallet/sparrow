package com.sparrowwallet.sparrow.control;

import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.sparrow.io.FileImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public abstract class FileImportPane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(FileImportPane.class);

    private final FileImport importer;
    protected Button importButton;
    private final SimpleStringProperty password = new SimpleStringProperty("");

    public FileImportPane(FileImport importer, String title, String description, String content, String imageUrl) {
        super(title, description, content, imageUrl);
        this.importer = importer;
    }

    @Override
    protected Control createButton() {
        importButton = new Button("Import File...");
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setOnAction(event -> {
            importFile();
        });
        return importButton;
    }

    private void importFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open " + importer.getWalletModel().toDisplayString() + " File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("JSON", "*.json"),
                new FileChooser.ExtensionFilter("TXT", "*.txt")
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
                    setDescription("Password Required");
                    showHideLink.setVisible(false);
                    setContent(getPasswordEntry(file));
                    importButton.setDisable(true);
                    setExpanded(true);
                } else {
                    InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                    importFile(file.getName(), inputStream, password);
                }
            } catch (Exception e) {
                log.error("Error importing file", e);
                String errorMessage = e.getMessage();
                if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                    errorMessage = e.getCause().getMessage();
                }
                if(e instanceof InvalidPasswordException || e.getCause() instanceof InvalidPasswordException) {
                    errorMessage = "Invalid wallet password";
                }
                if(e instanceof JsonParseException || e.getCause() instanceof JsonParseException) {
                    errorMessage = "File was not in JSON format";
                }
                setError("Import Error", errorMessage);
                importButton.setDisable(false);
            }
        }
    }

    protected abstract void importFile(String fileName, InputStream inputStream, String password) throws ImportException;

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
