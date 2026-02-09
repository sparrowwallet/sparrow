package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreCodexImport;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.util.List;

public class CodexKeystoreImportPane extends TitledDescriptionPane {
    protected final Wallet wallet;
    private final KeystoreCodexImport importer;
    private final KeyDerivation defaultDerivation;

    private SplitMenuButton importButton;

    private Button enterCodexButton;
    private Button calculateButton;

    protected Label validLabel;
    protected Label invalidLabel;

    protected final SimpleStringProperty secretShareProperty = new SimpleStringProperty("");

    public CodexKeystoreImportPane(Wallet wallet, KeystoreCodexImport importer, KeyDerivation defaultDerivation) {
        super(importer.getName(), "Enter secret share", importer.getKeystoreImportDescription(), importer.getWalletModel());
        this.wallet = wallet;
        this.importer = importer;
        this.defaultDerivation = defaultDerivation;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    @Override
    protected Control createButton() {
        enterCodexButton = new Button("Enter Secret Share");
        enterCodexButton.managedProperty().bind(enterCodexButton.visibleProperty());
        enterCodexButton.setOnAction(event -> {
            enterCodex();
        });

        return enterCodexButton;
    }

    private void enterCodex() {
        setDescription("Enter secret share");
        showHideLink.setVisible(false);
        setContent(getSecretShareEntry());
        setExpanded(true);
    }

    private void importKeystore(List<ChildNumber> derivation) {
        importButton.setDisable(true);
        try {
            Keystore keystore = importer.getKeystore(derivation, secretShareProperty.get());
            EventManager.get().post(new KeystoreImportEvent(keystore));
        } catch(ImportException e) {
            String errorMessage = e.getMessage();
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Import Error", errorMessage);
            importButton.setDisable(false);
        }
    }

    private void createImportButton() {
        importButton = new SplitMenuButton();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        setDefaultButton(importButton);
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importKeystore(getDefaultDerivation());
        });
        String[] accounts = new String[]{"Import Default Account #0", "Import Account #1", "Import Account #2", "Import Account #3", "Import Account #4", "Import Account #5", "Import Account #6", "Import Account #7", "Import Account #8", "Import Account #9"};
        int scriptAccountsLength = ScriptType.P2SH.equals(wallet.getScriptType()) ? 1 : accounts.length;
        for(int i = 0; i < scriptAccountsLength; i++) {
            MenuItem item = new MenuItem(accounts[i]);
            final List<ChildNumber> derivation = wallet.getScriptType().getDefaultDerivation(i);
            item.setOnAction(event -> {
                importButton.setDisable(true);
                importKeystore(derivation);
            });
            importButton.getItems().add(item);
        }

        importButton.managedProperty().bind(importButton.visibleProperty());
        importButton.setVisible(false);
    }

    private List<ChildNumber> getDefaultDerivation() {
        return defaultDerivation == null || defaultDerivation.getDerivation().isEmpty() ? wallet.getScriptType().getDefaultDerivation() : defaultDerivation.getDerivation();
    }

    private void onInputChange(boolean empty, boolean validChecksum) {
        if(!empty) {
            try {
                importer.getKeystore(ScriptType.P2WPKH.getDefaultDerivation(), secretShareProperty.get());
                validChecksum = true;
            } catch(ImportException e) {
                invalidLabel.setText("Invalid checksum");
                invalidLabel.setTooltip(null);
            }
        }

        calculateButton.setDisable(!validChecksum);
        validLabel.setVisible(validChecksum);
        invalidLabel.setVisible(!validChecksum && !empty);
    }

    private Node getSecretShareEntry() {
        VBox vBox = new VBox(20);
        vBox.setPadding(new Insets(10, 30, 10, 30));

        HBox shareEntry = new HBox(10);
        shareEntry.setAlignment(Pos.CENTER_LEFT);
        Label shareLabel = new Label("Secret:");
        TextField shareField = new TextField();
        HBox.setHgrow(shareField, Priority.ALWAYS);
        shareField.setPromptText("ms...");
        shareField.textProperty().addListener((observable, oldValue, newValue) -> {
            secretShareProperty.set(newValue);
        });
        shareEntry.getChildren().addAll(shareLabel, shareField);
        vBox.getChildren().add(shareEntry);

        AnchorPane buttonPane = new AnchorPane();

        validLabel = new Label("Valid checksum", GlyphUtils.getSuccessGlyph());
        validLabel.setContentDisplay(ContentDisplay.LEFT);
        validLabel.setGraphicTextGap(5.0);
        validLabel.managedProperty().bind(validLabel.visibleProperty());
        validLabel.setVisible(false);
        buttonPane.getChildren().add(validLabel);
        AnchorPane.setTopAnchor(validLabel, 5.0);
        AnchorPane.setLeftAnchor(validLabel, 0.0);

        invalidLabel = new Label("Invalid checksum", GlyphUtils.getInvalidGlyph());
        invalidLabel.setContentDisplay(ContentDisplay.LEFT);
        invalidLabel.setGraphicTextGap(5.0);
        invalidLabel.managedProperty().bind(invalidLabel.visibleProperty());
        invalidLabel.setVisible(false);
        buttonPane.getChildren().add(invalidLabel);
        AnchorPane.setTopAnchor(invalidLabel, 5.0);
        AnchorPane.setLeftAnchor(invalidLabel, 0.0);

        secretShareProperty.addListener((ChangeListener<String>) (c, oldval, newval) -> {
            boolean empty = secretShareProperty.isEmpty().get();
            boolean validChecksum = false;
            onInputChange(empty, validChecksum);
        });

        HBox rightBox = new HBox();
        rightBox.setSpacing(10);

        calculateButton = new Button("Create Keystore");
        calculateButton.setDisable(true);
        calculateButton.setDefaultButton(true);
        calculateButton.managedProperty().bind(calculateButton.visibleProperty());
        calculateButton.setTooltip(new Tooltip("Create the keystore from the provided secret share"));
        calculateButton.setOnAction(event -> {
            setExpanded(true);
            enterCodexButton.setVisible(false);
            importButton.setVisible(true);
            importButton.setDisable(false);
            setDescription("Ready to import");
            showHideLink.setText("Show Derivation...");
            showHideLink.setVisible(false);
            setContent(getDerivationEntry(getDefaultDerivation()));
        });

        rightBox.getChildren().add(calculateButton);

        buttonPane.getChildren().add(rightBox);
        AnchorPane.setRightAnchor(rightBox, 0.0);

        vBox.getChildren().add(buttonPane);

        Platform.runLater(shareField::requestFocus);

        return vBox;
    }

    private Node getDerivationEntry(List<ChildNumber> derivation) {
        TextField derivationField = new TextField();
        derivationField.setPromptText("Derivation path");
        derivationField.setText(KeyDerivation.writePath(derivation));
        HBox.setHgrow(derivationField, Priority.ALWAYS);

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(derivationField, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));

        Button importDerivationButton = new Button("Import Custom Derivation Keystore");
        importDerivationButton.setDisable(true);
        importDerivationButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            List<ChildNumber> importDerivation = KeyDerivation.parsePath(derivationField.getText());
            importKeystore(importDerivation);
        });

        derivationField.textProperty().addListener((observable, oldValue, newValue) -> {
            importButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue) || !KeyDerivation.parsePath(newValue).equals(derivation));
            importDerivationButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue) || KeyDerivation.parsePath(newValue).equals(derivation));
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(derivationField);
        contentBox.getChildren().add(importDerivationButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(60);

        return contentBox;
    }
}
