package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.SeedDisplayDialog;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.keystoreimport.KeystoreImportDialog;
import com.sparrowwallet.sparrow.event.SettingsChangedEvent;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.controlsfx.glyphfont.Glyph;
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
    private ToggleGroup keystoreSourceToggleGroup;

    @FXML
    private Label type;

    @FXML
    private Button importButton;

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

        derivation.setPromptText(getWalletForm().getWallet().getScriptType().getDefaultDerivationPath());

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
        keystoreSourceToggleGroup.selectToggle(null);
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
        if(keystore.getSource() == KeystoreSource.SW_SEED) {
            Glyph searchGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EYE);
            searchGlyph.setFontSize(12);
            type.setGraphic(searchGlyph);
        } else {
            type.setGraphic(null);
        }

        importButton.setText(keystore.getSource() == KeystoreSource.SW_WATCH ? "Import..." : "Edit...");

        boolean editable = (keystore.getSource() == KeystoreSource.SW_WATCH);
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
        KeystoreSource initialSource = keystore.getSource();
        if(initialSource == null || !KeystoreImportDialog.getSupportedSources().contains(initialSource)) {
            initialSource = KeystoreImportDialog.getSupportedSources().get(0);
        }

        launchImportDialog(initialSource);
    }

    private void launchImportDialog(KeystoreSource initialSource) {
        KeystoreImportDialog dlg = new KeystoreImportDialog(getWalletForm().getWallet(), initialSource);
        Optional<Keystore> result = dlg.showAndWait();
        if (result.isPresent()) {
            selectSourcePane.setVisible(false);

            Keystore importedKeystore = result.get();
            walletForm.getWallet().makeLabelsUnique(importedKeystore);
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

    public void showSeed(MouseEvent event) {
        int keystoreIndex = getWalletForm().getWallet().getKeystores().indexOf(keystore);
        Wallet copy = getWalletForm().getWallet().copy();

        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(getWalletForm().getWalletFile(), TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    showSeed(decryptedWallet.getKeystores().get(keystoreIndex));
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(getWalletForm().getWalletFile(), TimedEvent.Action.END, "Failed"));
                    AppController.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(getWalletForm().getWalletFile(), TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            showSeed(keystore);
        }
    }

    private void showSeed(Keystore keystore) {
        SeedDisplayDialog dlg = new SeedDisplayDialog(keystore);
        dlg.showAndWait();
    }

    @Subscribe
    public void update(SettingsChangedEvent event) {
        if(walletForm.getWallet().equals(event.getWallet()) && event.getType().equals(SettingsChangedEvent.Type.SCRIPT_TYPE)) {
            derivation.setPromptText(event.getWallet().getScriptType().getDefaultDerivationPath());
            if(!derivation.getText().isEmpty()) {
                String derivationPath = derivation.getText();
                derivation.setText(derivationPath + " ");
                derivation.setText(derivationPath);
            }
        }
    }
}
