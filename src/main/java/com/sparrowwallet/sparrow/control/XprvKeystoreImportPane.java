package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreXprvImport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.util.List;

public class XprvKeystoreImportPane extends TitledDescriptionPane {
    protected final Wallet wallet;
    protected final KeystoreXprvImport importer;

    private Button enterXprvButton;
    private SplitMenuButton importButton;

    private ExtendedKey xprv;

    public XprvKeystoreImportPane(Wallet wallet, KeystoreXprvImport importer) {
        super(importer.getName(), "Extended key import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.importer = importer;

        createImportButton();
        buttonBox.getChildren().add(importButton);
    }

    public XprvKeystoreImportPane(Keystore keystore) {
        super("Master Private Key", "BIP32 key", "", "image/" + WalletModel.SEED.getType() + ".png");
        this.wallet = null;
        this.importer = null;

        try {
            this.xprv = keystore.getExtendedMasterPrivateKey();
        } catch(MnemonicException e) {
            //can't happen
        }

        showHideLink.setVisible(false);
        buttonBox.getChildren().clear();
        setContent(getXprvEntry(true));
        setExpanded(true);
    }

    @Override
    protected Control createButton() {
        enterXprvButton = new Button("Enter Private Key");
        enterXprvButton.managedProperty().bind(enterXprvButton.visibleProperty());
        enterXprvButton.setOnAction(event -> {
            enterXprvButton.setDisable(true);
            enterXprv();
        });

        return enterXprvButton;
    }

    private void createImportButton() {
        importButton = new SplitMenuButton();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        importButton.getStyleClass().add("default-button");
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importKeystore(wallet.getScriptType().getDefaultDerivation());
        });
        String[] accounts = new String[] {"Import Default Account #0", "Import Account #1", "Import Account #2", "Import Account #3", "Import Account #4", "Import Account #5", "Import Account #6", "Import Account #7", "Import Account #8", "Import Account #9"};
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

    private void enterXprv() {
        setDescription("Enter master private key");
        showHideLink.setVisible(false);
        setContent(getXprvEntry(false));
        setExpanded(true);
    }

    private void importKeystore(List<ChildNumber> derivation) {
        importButton.setDisable(true);
        try {
            Keystore keystore = importer.getKeystore(derivation, xprv);
            EventManager.get().post(new KeystoreImportEvent(keystore));
        } catch (ImportException e) {
            String errorMessage = e.getMessage();
            if(e.getCause() != null && e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                errorMessage = e.getCause().getMessage();
            }
            setError("Import Error", errorMessage);
            importButton.setDisable(false);
        }
    }

    private Node getXprvEntry(boolean displayOnly) {
        TextArea xprvField = new TextArea();
        xprvField.setPrefRowCount(2);
        xprvField.setWrapText(true);
        xprvField.getStyleClass().add("fixed-width");
        xprvField.setPromptText(ExtendedKey.Header.fromScriptType(ScriptType.P2PKH, true).getName() + (wallet != null ? "/" + ExtendedKey.Header.fromScriptType(wallet.getScriptType(), true).getName() : "") + "...");
        HBox.setHgrow(xprvField, Priority.ALWAYS);

        if(xprv != null) {
            xprvField.setText(xprv.toString());
        }
        if(displayOnly) {
            xprvField.setEditable(false);
        }

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(xprvField, Validator.combine(
                Validator.createEmptyValidator("xprv is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid private key", !ExtendedKey.isValid(newValue) || ExtendedKey.fromDescriptor(newValue).getKey().isPubKeyOnly())
        ));

        Button importXprvButton = new Button("Import");
        importXprvButton.setMinWidth(80);
        importXprvButton.setDisable(true);
        importXprvButton.setOnAction(event -> {
            enterXprvButton.setVisible(false);
            importButton.setVisible(true);
            setDescription("Ready to import");
            xprv = ExtendedKey.fromDescriptor(xprvField.getText());
            setContent(getDerivationEntry(wallet.getScriptType().getDefaultDerivation()));
        });

        xprvField.textProperty().addListener((observable, oldValue, newValue) -> {
            importXprvButton.setDisable(newValue.isEmpty() || !ExtendedKey.isValid(newValue) || ExtendedKey.fromDescriptor(newValue).getKey().isPubKeyOnly());
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(xprvField);
        if(!displayOnly) {
            contentBox.getChildren().add(importXprvButton);
        }
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(100);

        return contentBox;
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
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
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
