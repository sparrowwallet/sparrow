package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.CardApi;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.keystoreimport.KeystoreImportDialog;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;

import javax.smartcardio.CardException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.io.CardApi.isReaderAvailable;

public class KeystoreController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(KeystoreController.class);

    public static final String DEFAULT_WATCH_ONLY_FINGERPRINT = "00000000";

    private Keystore keystore;

    @FXML
    private StackPane selectSourcePane;

    @FXML
    private ToggleGroup keystoreSourceToggleGroup;

    @FXML
    private Label type;

    @FXML
    private Button viewSeedButton;

    @FXML
    private Button viewKeyButton;

    @FXML
    private Button changePinButton;

    @FXML
    private Button importButton;

    @FXML
    private TextField label;

    @FXML
    private Field xpubField;

    @FXML
    private TextArea xpub;

    @FXML
    private TextField derivation;

    @FXML
    private TextField fingerprint;

    @FXML
    private LifeHashIcon fingerprintIcon;

    @FXML
    private Button scanXpubQR;

    @FXML
    private Button displayXpubQR;

    @FXML
    private Button switchXpubHeader;

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
        if(keystore.isValid() || keystore.getExtendedPublicKey() != null) {
            selectSourcePane.setVisible(false);
        } else if(!getWalletForm().getWallet().isMasterWallet() && keystore.getKeyDerivation() != null) {
           Wallet masterWallet = getWalletForm().getWallet().getMasterWallet();
           int keystoreIndex = getWalletForm().getWallet().getKeystores().indexOf(keystore);
           KeystoreSource keystoreSource = masterWallet.getKeystores().get(keystoreIndex).getSource();
           for(Toggle toggle : keystoreSourceToggleGroup.getToggles()) {
               ToggleButton toggleButton = (ToggleButton)toggle;
               toggleButton.setDisable(toggleButton.getUserData() != keystoreSource);
           }
        }

        viewSeedButton.managedProperty().bind(viewSeedButton.visibleProperty());
        viewKeyButton.managedProperty().bind(viewKeyButton.visibleProperty());
        changePinButton.managedProperty().bind(changePinButton.visibleProperty());
        scanXpubQR.managedProperty().bind(scanXpubQR.visibleProperty());
        displayXpubQR.managedProperty().bind(displayXpubQR.visibleProperty());
        displayXpubQR.visibleProperty().bind(scanXpubQR.visibleProperty().not());

        updateType();

        label.setText(keystore.getLabel());

        derivation.setPromptText(getWalletForm().getWallet().getScriptType().getDefaultDerivationPath());

        if(keystore.getExtendedPublicKey() != null) {
            xpub.setText(keystore.getExtendedPublicKey().toString());
            setXpubContext(keystore.getExtendedPublicKey());
        } else {
            switchXpubHeader.setDisable(true);
            xpubField.setText(Network.get().getXpubHeader().getDisplayName() + ":");
        }

        if(keystore.getKeyDerivation() != null) {
            derivation.setText(keystore.getKeyDerivation().getDerivationPath());
            fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
            fingerprintIcon.setHex(fingerprint.getText());
        } else {
            keystore.setKeyDerivation(new KeyDerivation("",""));
        }

        label.textProperty().addListener((observable, oldValue, newValue) -> {
            keystore.setLabel(newValue);
            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_LABEL));
        });
        fingerprint.textProperty().addListener((observable, oldValue, newValue) -> {
            keystore.setKeyDerivation(new KeyDerivation(newValue, keystore.getKeyDerivation().getDerivationPath()));
            fingerprintIcon.setHex(newValue.length() == 8 ? newValue : null);
            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_FINGERPRINT));
        });
        fingerprint.setTextFormatter(new TextFormatter<>(change -> {
            String input = change.getText();
            if(input.matches("[0-9a-fA-F]*")) {
                return change;
            }
            return null;
        }));
        derivation.textProperty().addListener((observable, oldValue, newValue) -> {
            if(KeyDerivation.isValid(newValue) && !walletForm.getWallet().derivationMatchesAnotherScriptType(newValue)) {
                keystore.setKeyDerivation(new KeyDerivation(keystore.getKeyDerivation().getMasterFingerprint(), newValue));
                EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_DERIVATION));
            }
        });
        xpub.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean valid = ExtendedKey.isValid(newValue);
            if(valid) {
                ExtendedKey extendedKey = ExtendedKey.fromDescriptor(newValue);
                setXpubContext(extendedKey);
                if(!extendedKey.equals(keystore.getExtendedPublicKey()) && extendedKey.getKey().isPubKeyOnly()) {
                    keystore.setExtendedPublicKey(extendedKey);
                    EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.KEYSTORE_XPUB));
                }
            } else {
                xpub.setContextMenu(null);
                switchXpubHeader.setDisable(true);
            }
            scanXpubQR.setVisible(!valid);
        });

        setInputFieldsDisabled(keystore.getSource() != KeystoreSource.SW_WATCH && (!walletForm.getWallet().isMasterWallet() || !walletForm.getWallet().getChildWallets().isEmpty()));
    }

    private void setXpubContext(ExtendedKey extendedKey) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyXPub = new MenuItem("Copy " + Network.get().getXpubHeader().getDisplayName());
        copyXPub.setOnAction(AE -> {
            contextMenu.hide();
            ClipboardContent content = new ClipboardContent();
            content.putString(extendedKey.toString());
            Clipboard.getSystemClipboard().setContent(content);
        });
        contextMenu.getItems().add(copyXPub);

        ExtendedKey.Header header = ExtendedKey.Header.fromScriptType(walletForm.getWallet().getScriptType(), false);
        if(header != Network.get().getXpubHeader()) {
            String otherPub = extendedKey.getExtendedKey(header);

            MenuItem copyOtherPub = new MenuItem("Copy " + header.getDisplayName());
            copyOtherPub.setOnAction(AE -> {
                contextMenu.hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(otherPub);
                Clipboard.getSystemClipboard().setContent(content);
            });
            contextMenu.getItems().add(copyOtherPub);

            xpubField.setText(Network.get().getXpubHeader().getDisplayName() + " / " + header.getDisplayName() + ":");
            switchXpubHeader.setDisable(false);
            switchXpubHeader.setTooltip(new Tooltip("Show as " + header.getDisplayName()));
        } else {
            xpubField.setText(Network.get().getXpubHeader().getDisplayName() + ":");
            switchXpubHeader.setDisable(true);
        }

        xpub.setContextMenu(contextMenu);
        scanXpubQR.setVisible(false);
    }

    public void selectSource(ActionEvent event) {
        keystoreSourceToggleGroup.selectToggle(null);
        ToggleButton sourceButton = (ToggleButton)event.getSource();
        KeystoreSource keystoreSource = (KeystoreSource)sourceButton.getUserData();
        if(keystoreSource != KeystoreSource.SW_WATCH) {
            launchImportDialog(keystoreSource);
        } else {
            fingerprint.setText(DEFAULT_WATCH_ONLY_FINGERPRINT);
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
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());

        validationSupport.registerValidator(label, Validator.combine(
                Validator.createEmptyValidator("Label is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is not unique", walletForm.getWallet().getKeystores().stream().filter(k -> k != keystore).map(Keystore::getLabel).collect(Collectors.toList()).contains(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is too long", newValue.replace(" ", "").length() > 16)
        ));

        validationSupport.registerValidator(xpub, Validator.combine(
                Validator.createEmptyValidator(Network.get().getXpubHeader().getDisplayName() + " is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, Network.get().getXpubHeader().getDisplayName() + " is invalid", !ExtendedKey.isValid(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Extended key is not unique", ExtendedKey.isValid(newValue) &&
                        walletForm.getWallet().getKeystores().stream().filter(k -> k != keystore && k.getExtendedPublicKey() != null).map(Keystore::getExtendedPublicKey).collect(Collectors.toList()).contains(ExtendedKey.fromDescriptor(newValue)))
        ));

        validationSupport.registerValidator(derivation, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Derivation is invalid", !KeyDerivation.isValid(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Derivation matches another script type", walletForm.getWallet().derivationMatchesAnotherScriptType(newValue))
        ));

        validationSupport.registerValidator(fingerprint, Validator.combine(
                Validator.createEmptyValidator("Master fingerprint is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Master fingerprint is invalid", (newValue == null || newValue.length() != 8 || !Utils.isHex(newValue)))
        ));
    }

    private void updateType() {
        type.setText(getTypeLabel(keystore));
        type.setGraphic(getTypeIcon(keystore));
        viewSeedButton.setVisible(keystore.getSource() == KeystoreSource.SW_SEED && keystore.hasSeed());
        viewKeyButton.setVisible(keystore.getSource() == KeystoreSource.SW_SEED && keystore.hasMasterPrivateExtendedKey());
        changePinButton.setVisible(keystore.getWalletModel().isCard());

        importButton.setText(keystore.getSource() == KeystoreSource.SW_WATCH ? "Import..." : "Replace...");
        importButton.setTooltip(new Tooltip(keystore.getSource() == KeystoreSource.SW_WATCH ? "Import a keystore from an external source" : "Replace this keystore with another source"));

        boolean editable = (keystore.getSource() == KeystoreSource.SW_WATCH);
        setEditable(fingerprint, editable);
        setEditable(derivation, editable);
        setEditable(xpub, editable);
        scanXpubQR.setVisible(editable);
    }

    private void setEditable(TextInputControl textInputControl, boolean editable) {
        textInputControl.setEditable(editable);
        if(!editable && !textInputControl.getStyleClass().contains("readonly")) {
            textInputControl.getStyleClass().add("readonly");
        } else if(editable) {
            textInputControl.getStyleClass().remove("readonly");
        }
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

    private Node getTypeIcon(Keystore keystore) {
        return new WalletIcon(getWalletForm().getStorage(), getWalletForm().getWallet(), keystore, getDefaultTypeIcon(keystore));
    }

    private Glyph getDefaultTypeIcon(Keystore keystore) {
        switch (keystore.getSource()) {
            case HW_USB:
                return new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
            case HW_AIRGAPPED:
                return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SD_CARD);
            case SW_SEED:
                return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LAPTOP);
            case SW_WATCH:
            default:
                return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EYE);
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
        boolean restrictSource = keystoreSourceToggleGroup.getToggles().stream().anyMatch(toggle -> ((ToggleButton)toggle).isDisabled());
        KeyDerivation requiredDerivation = restrictSource ? keystore.getKeyDerivation() : null;
        WalletModel requiredModel = restrictSource ? keystore.getWalletModel() : null;
        KeystoreImportDialog dlg = new KeystoreImportDialog(getWalletForm().getWallet(), initialSource, requiredDerivation, requiredModel, restrictSource);
        Optional<Keystore> result = dlg.showAndWait();
        if(result.isPresent()) {
            selectSourcePane.setVisible(false);

            Keystore importedKeystore = result.get();
            walletForm.getWallet().makeLabelsUnique(importedKeystore);
            keystore.setSource(importedKeystore.getSource());
            keystore.setWalletModel(importedKeystore.getWalletModel());
            keystore.setLabel(importedKeystore.getLabel());
            keystore.setKeyDerivation(importedKeystore.getKeyDerivation());
            keystore.setExtendedPublicKey(importedKeystore.getExtendedPublicKey());
            keystore.setMasterPrivateExtendedKey(importedKeystore.getMasterPrivateExtendedKey());
            keystore.setSeed(importedKeystore.getSeed());
            keystore.setBip47ExtendedPrivateKey(importedKeystore.getBip47ExtendedPrivateKey());

            updateType();
            label.setText(keystore.getLabel());
            fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
            derivation.setText(keystore.getKeyDerivation().getDerivationPath());

            if(keystore.getExtendedPublicKey() != null) {
                xpub.setText(keystore.getExtendedPublicKey().toString());
                setXpubContext(keystore.getExtendedPublicKey());
            } else {
                xpub.setText("");
            }
        }
    }

    public void showPrivate(ActionEvent event) {
        int keystoreIndex = getWalletForm().getWallet().getKeystores().indexOf(keystore);
        Wallet copy = getWalletForm().getWallet().copy();

        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(getWalletForm().getWalletId(), TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    showPrivate(decryptedWallet.getKeystores().get(keystoreIndex));
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(getWalletForm().getWalletId(), TimedEvent.Action.END, "Failed"));
                    AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(getWalletForm().getWalletId(), TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            showPrivate(keystore);
        }
    }

    private void showPrivate(Keystore keystore) {
        if(keystore.hasSeed()) {
            SeedDisplayDialog dlg = new SeedDisplayDialog(keystore);
            dlg.showAndWait();
        } else if(keystore.hasMasterPrivateExtendedKey()) {
            MasterKeyDisplayDialog dlg = new MasterKeyDisplayDialog(keystore);
            dlg.showAndWait();
        }
    }

    public void changeCardPin(ActionEvent event) {
        if(!isReaderAvailable()) {
            AppServices.showErrorDialog("No card reader", "Connect a card reader to change the card PIN.");
            return;
        }

        CardPinDialog cardPinDialog = new CardPinDialog();
        Optional<CardPinDialog.CardPinChange> optPinChange = cardPinDialog.showAndWait();
        if(optPinChange.isPresent()) {
            String currentPin = optPinChange.get().currentPin();
            String newPin = optPinChange.get().newPin();
            boolean backupFirst = optPinChange.get().backupFirst();
            try {
                CardApi cardApi = CardApi.getCardApi(keystore.getWalletModel(), currentPin);
                Service<Void> authDelayService = cardApi.getAuthDelayService();
                if(authDelayService != null) {
                    authDelayService.setOnSucceeded(event1 -> {
                        try {
                            changeCardPin(newPin, backupFirst, cardApi);
                        } catch(CardException e) {
                            log.error("Error communicating with card", e);
                            AppServices.showErrorDialog("Error communicating with card", e.getMessage());
                        }
                    });
                    authDelayService.setOnFailed(event1 -> {
                        Throwable e = event1.getSource().getException();
                        log.error("Error communicating with card", e);
                        AppServices.showErrorDialog("Error communicating with card", e.getMessage());
                    });
                    ServiceProgressDialog serviceProgressDialog = new ServiceProgressDialog("Authentication Delay", "Waiting for authentication delay to clear...", "/image/tapsigner.png", authDelayService);
                    AppServices.moveToActiveWindowScreen(serviceProgressDialog);
                    authDelayService.start();
                } else {
                    changeCardPin(newPin, backupFirst, cardApi);
                }
            } catch(CardException e) {
                log.error("Error communicating with card", e);
                AppServices.showErrorDialog("Error communicating with card", e.getMessage());
            }
        }
    }

    private void changeCardPin(String newPin, boolean backupFirst, CardApi cardApi) throws CardException {
        boolean requiresBackup = cardApi.requiresBackup();
        if(backupFirst || requiresBackup) {
            Service<String> backupService = cardApi.getBackupService();
            backupService.setOnSucceeded(event -> {
                String backup = backupService.getValue();
                TextAreaDialog backupDialog = new TextAreaDialog(backup, false);
                backupDialog.setTitle("Backup Private Key");
                backupDialog.getDialogPane().setHeaderText((requiresBackup ? "Please backup first by saving" : "Save") + " the following text in a safe place. It contains an encrypted copy of the card's private key, and can be decrypted using the backup key written on the back of the card.");
                backupDialog.showAndWait();
                try {
                    changePin(newPin, cardApi);
                } catch(Exception e) {
                    log.error("Error communicating with card", e);
                    AppServices.showErrorDialog("Error communicating with card", e.getMessage());
                }
            });
            backupService.setOnFailed(event -> {
                Throwable e = event.getSource().getException();
                log.error("Error communicating with card", e);
                AppServices.showErrorDialog("Error communicating with card", e.getMessage());
            });
            backupService.start();
        } else {
            changePin(newPin, cardApi);
        }
    }

    private void changePin(String newPin, CardApi cardApi) throws CardException {
        if(cardApi.changePin(newPin)) {
            AppServices.showSuccessDialog("PIN changed", "The card's PIN has been changed.");
        } else {
            AppServices.showSuccessDialog("Could not change PIN", "The card's PIN was not changed.");
        }
    }

    public void scanXpubQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.extendedKey != null && result.extendedKey.getKey().isPubKeyOnly()) {
                xpub.setText(result.extendedKey.getExtendedKey());
            } else if(result.outputDescriptor != null && !result.outputDescriptor.getExtendedPublicKeys().isEmpty()) {
                ExtendedKey extendedKey = result.outputDescriptor.getExtendedPublicKeys().iterator().next();
                KeyDerivation keyDerivation = result.outputDescriptor.getKeyDerivation(extendedKey);
                fingerprint.setText(keyDerivation.getMasterFingerprint());
                derivation.setText(keyDerivation.getDerivationPath());
                xpub.setText(extendedKey.toString());
            } else if(result.wallets != null) {
                for(Wallet wallet : result.wallets) {
                    if(getWalletForm().getWallet().getScriptType().equals(wallet.getScriptType()) && !wallet.getKeystores().isEmpty()) {
                        Keystore keystore = wallet.getKeystores().get(0);
                        fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
                        derivation.setText(keystore.getKeyDerivation().getDerivationPath());
                        xpub.setText(keystore.getExtendedPublicKey().toString());
                        return;
                    }
                }

                AppServices.showErrorDialog("Missing Script Type", "QR Code did not contain any information for the " + getWalletForm().getWallet().getScriptType().getDescription() + " script type.");
            } else if(result.seed != null) {
                try {
                    Keystore keystore = Keystore.fromSeed(result.seed, getWalletForm().getWallet().getScriptType().getDefaultDerivation());
                    fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
                    derivation.setText(keystore.getKeyDerivation().getDerivationPath());
                    xpub.setText(keystore.getExtendedPublicKey().toString());
                } catch(MnemonicException e) {
                    log.error("Error parsing seed", e);
                    AppServices.showErrorDialog("Error parsing seed", e.getMessage());
                } finally {
                    result.seed.clear();
                }
            } else if(result.payload != null) {
                try {
                    OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor("sh(" + result.payload + ")");
                    Wallet wallet = outputDescriptor.toWallet();
                    Keystore keystore = wallet.getKeystores().get(0);
                    fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
                    derivation.setText(keystore.getKeyDerivation().getDerivationPath());
                    xpub.setText(keystore.getExtendedPublicKey().toString());
                } catch(Exception e) {
                    AppServices.showErrorDialog("Invalid QR Code", "QR Code did not contain a valid " + Network.get().getXpubHeader().getDisplayName());
                }
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                AppServices.showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "QR Code did not contain a valid " + Network.get().getXpubHeader().getDisplayName());
            }
        }
    }

    public void displayXpubQR(ActionEvent event) {
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(xpub.getText());
        qrDisplayDialog.showAndWait();
    }

    public void switchXpubHeader(ActionEvent event) {
        if(keystore.getExtendedPublicKey() != null) {
            ExtendedKey.Header header = ExtendedKey.Header.fromScriptType(walletForm.getWallet().getScriptType(), false);
            if(!xpub.getText().startsWith(header.getName())) {
                String otherPub = keystore.getExtendedPublicKey().getExtendedKey(header);
                xpub.setText(otherPub);
                switchXpubHeader.setTooltip(new Tooltip("Show as " + Network.get().getXpubHeader().getDisplayName()));
            } else {
                String xPub = keystore.getExtendedPublicKey().getExtendedKey();
                xpub.setText(xPub);
                switchXpubHeader.setTooltip(new Tooltip("Show as " + header.getDisplayName()));
            }
        }
    }

    private void setInputFieldsDisabled(boolean disabled) {
        setEditable(fingerprint, !disabled);
        setEditable(derivation, !disabled);
        setEditable(xpub, !disabled);
        importButton.setDisable(disabled);
    }

    @Subscribe
    public void childWalletsAdded(ChildWalletsAddedEvent event) {
        if(event.getMasterWalletId().equals(walletForm.getWalletId())) {
            setInputFieldsDisabled(keystore.getSource() != KeystoreSource.SW_WATCH);
        }
    }

    @Subscribe
    public void update(SettingsChangedEvent event) {
        if(walletForm.getWallet().equals(event.getWallet()) && event.getType().equals(SettingsChangedEvent.Type.SCRIPT_TYPE)) {
            derivation.setPromptText(event.getWallet().getScriptType().getDefaultDerivationPath());
            if(derivation.getText() != null && !derivation.getText().isEmpty()) {
                String derivationPath = derivation.getText();
                derivation.setText(derivationPath + " ");
                derivation.setText(derivationPath);
            }
            if(keystore.getExtendedPublicKey() != null) {
                setXpubContext(keystore.getExtendedPublicKey());
            }
        }
    }
}
