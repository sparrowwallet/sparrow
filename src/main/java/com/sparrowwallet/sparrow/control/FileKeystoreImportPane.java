package com.sparrowwallet.sparrow.control;

import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.KeystoreFileImport;
import com.sparrowwallet.sparrow.io.KeystoreImport;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;

import java.io.*;

public class FileKeystoreImportPane extends KeystoreImportPane {
    private final KeystoreFileImport importer;
    private Button importButton;
    private final SimpleStringProperty password = new SimpleStringProperty("");

    public FileKeystoreImportPane(KeystoreImportAccordion importAccordion, Wallet wallet, KeystoreFileImport importer) {
        super(importAccordion, wallet, importer);
        this.importer = importer;
    }

    @Override
    protected Node getTitle(KeystoreImport importer) {
        Node title = super.getTitle(importer);

        setDescription("Keystore file import");

        importButton = new Button("Import File...");
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setOnAction(event -> {
            importFile();
        });
        buttonBox.getChildren().add(importButton);

        return title;
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
                    setDescription("Password Required");
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
                String errorMessage = e.getMessage();
                if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                    errorMessage = e.getCause().getMessage();
                }
                if(e instanceof ECIESKeyCrypter.InvalidPasswordException || e.getCause() instanceof ECIESKeyCrypter.InvalidPasswordException) {
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
