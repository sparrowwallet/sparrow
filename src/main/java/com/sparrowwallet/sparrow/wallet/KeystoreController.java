package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.keystoreimport.KeystoreImportDialog;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class KeystoreController extends WalletFormController implements Initializable {
    private Keystore keystore;

    @FXML
    private StackPane selectSourcePane;

    @FXML
    private Label type;

    @FXML
    private TextField label;

    @FXML
    private TextArea xpub;

    @FXML
    private TextField derivation;

    @FXML
    private TextField fingerprint;

    private final ValidationSupport validationSupport = new ValidationSupport();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void setKeystore(WalletForm walletForm, Keystore keystore) {
        this.keystore = keystore;
        setWalletForm(walletForm);
    }

    @Override
    public void initializeView() {
        Platform.runLater(this::setupValidation);

        selectSourcePane.managedProperty().bind(selectSourcePane.visibleProperty());
        if(keystore.isValid()) {
            selectSourcePane.setVisible(false);
        }

        updateType();

        label.setText(keystore.getLabel());

        if(keystore.getExtendedPublicKey() != null) {
            xpub.setText(keystore.getExtendedPublicKey().toString());
        }

        if(keystore.getKeyDerivation() != null) {
            derivation.setText(keystore.getKeyDerivation().getDerivationPath());
            fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
        } else {
            keystore.setKeyDerivation(new KeyDerivation("",""));
        }

        label.textProperty().addListener((observable, oldValue, newValue) -> {
            keystore.setLabel(newValue);
            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_LABEL));
        });
        fingerprint.textProperty().addListener((observable, oldValue, newValue) -> {
            keystore.setKeyDerivation(new KeyDerivation(newValue, keystore.getKeyDerivation().getDerivationPath()));
            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_FINGERPRINT));
        });
        derivation.textProperty().addListener((observable, oldValue, newValue) -> {
            if(KeyDerivation.isValid(newValue) && !walletForm.getWallet().derivationMatchesAnotherScriptType(newValue)) {
                keystore.setKeyDerivation(new KeyDerivation(keystore.getKeyDerivation().getMasterFingerprint(), newValue));
                EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_DERIVATION));
            }
        });
        xpub.textProperty().addListener((observable, oldValue, newValue) -> {
            if(ExtendedKey.isValid(newValue)) {
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(newValue));
                EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_XPUB));
            }
        });
    }

    public void selectSource(ActionEvent event) {
        ToggleButton sourceButton = (ToggleButton)event.getSource();
        KeystoreSource keystoreSource = (KeystoreSource)sourceButton.getUserData();
        if(keystoreSource != KeystoreSource.SW_WATCH) {
            launchImportDialog(keystoreSource);
        } else {
            selectSourcePane.setVisible(false);
        }
    }

    public TextField getLabel() {
        return label;
    }

    public ValidationSupport getValidationSupport() {
        return validationSupport;
    }

    private void setupValidation() {
        validationSupport.registerValidator(label, Validator.combine(
                Validator.createEmptyValidator("Label is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is not unique", walletForm.getWallet().getKeystores().stream().filter(k -> k != keystore).map(Keystore::getLabel).collect(Collectors.toList()).contains(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is too long", newValue.replace(" ", "").length() > 16)
        ));

        validationSupport.registerValidator(xpub, Validator.combine(
                Validator.createEmptyValidator("xPub is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "xPub is invalid", !ExtendedKey.isValid(newValue))
        ));

        validationSupport.registerValidator(derivation, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Derivation is invalid", !KeyDerivation.isValid(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Derivation matches another script type", walletForm.getWallet().derivationMatchesAnotherScriptType(newValue))
        ));

        validationSupport.registerValidator(fingerprint, Validator.combine(
                Validator.createEmptyValidator("Master fingerprint is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Master fingerprint is invalid", (newValue.length() != 8 || !Utils.isHex(newValue)))
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    private void updateType() {
        type.setText(getTypeLabel(keystore));

        boolean editable = (keystore.getSource() == KeystoreSource.SW_WATCH);
        label.setEditable(editable);
        fingerprint.setEditable(editable);
        derivation.setEditable(editable);
        xpub.setEditable(editable);
    }

    private String getTypeLabel(Keystore keystore) {
        switch (keystore.getSource()) {
            case HW_USB:
                return "Connected Hardware Wallet (" + keystore.getWalletModel().toDisplayString() + ")";
            case HW_AIRGAPPED:
                return "Airgapped Hardware Wallet (" + keystore.getWalletModel().toDisplayString() + ")";
            case SW_SEED:
                return "Software Wallet";
            case SW_WATCH:
            default:
                return "Watch Only Wallet";
        }
    }

    public void importKeystore(ActionEvent event) {
        launchImportDialog(KeystoreSource.HW_USB);
    }

    private void launchImportDialog(KeystoreSource initialSource) {
        KeystoreImportDialog dlg = new KeystoreImportDialog(getWalletForm().getWallet(), initialSource);
        Optional<Keystore> result = dlg.showAndWait();
        if (result.isPresent()) {
            selectSourcePane.setVisible(false);

            Keystore importedKeystore = result.get();
            keystore.setSource(importedKeystore.getSource());
            keystore.setWalletModel(importedKeystore.getWalletModel());
            keystore.setLabel(importedKeystore.getLabel());
            keystore.setKeyDerivation(importedKeystore.getKeyDerivation());
            keystore.setExtendedPublicKey(importedKeystore.getExtendedPublicKey());
            keystore.setSeed(importedKeystore.getSeed());

            updateType();
            label.setText(keystore.getLabel());
            fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
            derivation.setText(keystore.getKeyDerivation().getDerivationPath());
            xpub.setText(keystore.getExtendedPublicKey().toString());
        }
    }

    @Subscribe
    public void update(SettingsChangedEvent event) {
        if(walletForm.getWallet().equals(event.getWallet()) && event.getType().equals(SettingsChangedEvent.Type.SCRIPT_TYPE) && !derivation.getText().isEmpty()) {
            String derivationPath = derivation.getText();
            derivation.setText(derivationPath + " ");
            derivation.setText(derivationPath);
        }
    }
}
