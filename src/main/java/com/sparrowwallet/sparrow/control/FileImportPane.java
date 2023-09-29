package com.sparrowwallet.sparrow.control;

import com.google.gson.JsonParseException;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.FileImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public abstract class FileImportPane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(FileImportPane.class);

    private final FileImport importer;
    protected ButtonBase importButton;
    private final SimpleStringProperty password = new SimpleStringProperty("");
    private final boolean scannable;
    private final boolean fileFormatAvailable;
    protected List<Wallet> wallets;

    public FileImportPane(FileImport importer, String title, String description, String content, String imageUrl, boolean scannable, boolean fileFormatAvailable) {
        super(title, description, content, imageUrl);
        this.importer = importer;
        this.scannable = scannable;
        this.fileFormatAvailable = fileFormatAvailable;

        buttonBox.getChildren().clear();
        buttonBox.getChildren().add(createButton());
    }

    @Override
    protected Control createButton() {
        if(scannable && fileFormatAvailable) {
            ToggleButton scanButton = new ToggleButton("Scan...");
            Glyph cameraGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CAMERA);
            cameraGlyph.setFontSize(12);
            scanButton.setGraphic(cameraGlyph);
            scanButton.setOnAction(event -> {
                scanButton.setSelected(false);
                importQR();
            });

            ToggleButton fileButton = new ToggleButton("Import File...");
            fileButton.setAlignment(Pos.CENTER_RIGHT);
            fileButton.setOnAction(event -> {
                fileButton.setSelected(false);
                importFile();
            });
            importButton = fileButton;

            SegmentedButton segmentedButton = new SegmentedButton();
            segmentedButton.getButtons().addAll(scanButton, fileButton);
            return segmentedButton;
        } else if(scannable) {
            importButton = new Button("Scan...");
            Glyph cameraGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CAMERA);
            cameraGlyph.setFontSize(12);
            importButton.setGraphic(cameraGlyph);
            importButton.setAlignment(Pos.CENTER_RIGHT);
            importButton.setOnAction(event -> {
                importQR();
            });
            return importButton;
        } else {
            importButton = new Button("Import File...");
            importButton.setAlignment(Pos.CENTER_RIGHT);
            importButton.setOnAction(event -> {
                importFile();
            });
            return importButton;
        }
    }

    private void importFile() {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open " + importer.getWalletModel().toDisplayString() + " File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", Platform.getCurrent().equals(Platform.UNIX) ? "*" : "*.*"),
                new FileChooser.ExtensionFilter("JSON", "*.json"),
                new FileChooser.ExtensionFilter("TXT", "*.txt")
        );

        AppServices.moveToActiveWindowScreen(window, 800, 450);
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
                    try(InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                        importFile(file.getName(), inputStream, password);
                    };
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

    private void importQR() {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(this.getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.wallets != null) {
                wallets = result.wallets;
                try {
                    importFile(importer.getName(), null, null);
                } catch(ImportException e) {
                    log.error("Error importing QR", e);
                    setError("Import Error", e.getMessage());
                }
            } else if(result.outputDescriptor != null) {
                try {
                    wallets = List.of(result.outputDescriptor.toWallet());
                    importFile(importer.getName(), null, null);
                } catch(ImportException e) {
                    log.error("Error importing QR", e);
                    setError("Import Error", e.getMessage());
                }
            } else if(result.payload != null) {
                try {
                    importFile(importer.getName(), new ByteArrayInputStream(result.payload.getBytes(StandardCharsets.UTF_8)), null);
                } catch(Exception e) {
                    log.error("Error importing QR", e);
                    String errorMessage = e.getMessage();
                    if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                        errorMessage = e.getCause().getMessage();
                    }
                    if(e instanceof JsonParseException || e.getCause() instanceof JsonParseException) {
                        errorMessage = "QR contents were not in JSON format";
                    }
                    setError("Import Error", errorMessage);
                }
            } else if(result.exception != null) {
                log.error("Error importing QR", result.exception);
                setError("Import Error", result.exception.getMessage());
            } else {
                setError("Import Error", null);
                setExpanded(true);
            }
        }
    }

    protected List<Wallet> getScannedWallets() {
        return wallets;
    }

    protected Keystore getScannedKeystore(ScriptType scriptType) throws ImportException {
        if(wallets != null) {
            for(Wallet wallet : wallets) {
                if(scriptType.equals(wallet.getScriptType()) && !wallet.getKeystores().isEmpty()) {
                    Keystore keystore = wallet.getKeystores().get(0);
                    keystore.setLabel(importer.getName().replace(" Multisig", ""));
                    keystore.setSource(KeystoreSource.HW_AIRGAPPED);
                    keystore.setWalletModel(importer.getWalletModel());
                    return keystore;
                }
            }

            throw new ImportException("Script type " + scriptType.getDescription() + " is not supported in this QR. Check you are displaying the correct QR code.");
        }

        return null;
    }

    protected abstract void importFile(String fileName, InputStream inputStream, String password) throws ImportException;

    private Node getPasswordEntry(File file) {
        CustomPasswordField passwordField = new ViewPasswordField();
        passwordField.setPromptText("Password");
        password.bind(passwordField.textProperty());
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        Button importEncryptedButton = new Button("Import");
        importEncryptedButton.setDefaultButton(true);
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
