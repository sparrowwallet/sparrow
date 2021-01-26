package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.AddressDisplayedEvent;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.event.MessageSignedEvent;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DevicePane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(DevicePane.class);

    private final DeviceOperation deviceOperation;
    private final Wallet wallet;
    private final PSBT psbt;
    private final KeyDerivation keyDerivation;
    private final String message;
    private final Device device;

    private CustomPasswordField pinField;
    private Button unlockButton;
    private Button enterPinButton;
    private Button setPassphraseButton;
    private SplitMenuButton importButton;
    private Button signButton;
    private Button displayAddressButton;
    private Button signMessageButton;

    private final SimpleStringProperty passphrase = new SimpleStringProperty("");

    public DevicePane(Wallet wallet, Device device) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.IMPORT;
        this.wallet = wallet;
        this.psbt = null;
        this.keyDerivation = null;
        this.message = null;
        this.device = device;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createImportButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, importButton);
    }

    public DevicePane(PSBT psbt, Device device) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.SIGN;
        this.wallet = null;
        this.psbt = psbt;
        this.keyDerivation = null;
        this.message = null;
        this.device = device;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createSignButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, signButton);
    }

    public DevicePane(Wallet wallet, KeyDerivation keyDerivation, Device device) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.DISPLAY_ADDRESS;
        this.wallet = wallet;
        this.psbt = null;
        this.keyDerivation = keyDerivation;
        this.message = null;
        this.device = device;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createDisplayAddressButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, displayAddressButton);
    }

    public DevicePane(Wallet wallet, String message, KeyDerivation keyDerivation, Device device) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.SIGN_MESSAGE;
        this.wallet = wallet;
        this.psbt = null;
        this.keyDerivation = keyDerivation;
        this.message = message;
        this.device = device;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createSignMessageButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, signMessageButton);
    }

    private void initialise(Device device) {
        if(device.getNeedsPinSent() != null && device.getNeedsPinSent()) {
            unlockButton.setVisible(true);
        } else if(device.getNeedsPassphraseSent() != null && device.getNeedsPassphraseSent()) {
            setPassphraseButton.setVisible(true);
        } else if(device.getError() != null) {
            setError("Error", device.getError());
            Platform.runLater(() -> {
                setExpanded(true);
            });
        } else {
            showOperationButton();
        }
    }

    @Override
    protected Control createButton() {
        createUnlockButton();
        return unlockButton;
    }

    private void setDefaultStatus() {
        setDescription(device.getNeedsPinSent() ? "Locked" : device.getNeedsPassphraseSent() ? "Passphrase Required" : "Unlocked");
    }

    private void createUnlockButton() {
        unlockButton = new Button("Unlock");
        unlockButton.setAlignment(Pos.CENTER_RIGHT);
        unlockButton.setOnAction(event -> {
            unlockButton.setDisable(true);
            unlock(device);
        });
        unlockButton.managedProperty().bind(unlockButton.visibleProperty());
        unlockButton.setVisible(false);
    }

    private void createSetPassphraseButton() {
        setPassphraseButton = new Button("Set Passphrase");
        setPassphraseButton.setAlignment(Pos.CENTER_RIGHT);
        setPassphraseButton.setOnAction(event -> {
            setPassphraseButton.setDisable(true);
            setContent(getPassphraseEntry());
            setExpanded(true);
        });
        setPassphraseButton.managedProperty().bind(setPassphraseButton.visibleProperty());
        setPassphraseButton.setVisible(false);
    }

    private void createImportButton() {
        importButton = new SplitMenuButton();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            importKeystore(wallet.getScriptType().getDefaultDerivation());
        });
        String[] accounts = new String[] {"Default Account #0", "Account #1", "Account #2", "Account #3", "Account #4", "Account #5", "Account #6", "Account #7", "Account #8", "Account #9"};
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

    private void createSignButton() {
        signButton = new Button("Sign");
        signButton.setDefaultButton(true);
        signButton.setAlignment(Pos.CENTER_RIGHT);
        signButton.setMinWidth(44);
        signButton.setOnAction(event -> {
            signButton.setDisable(true);
            sign();
        });
        signButton.managedProperty().bind(signButton.visibleProperty());
        signButton.setVisible(false);
    }

    private void createDisplayAddressButton() {
        displayAddressButton = new Button("Display Address");
        displayAddressButton.setDefaultButton(true);
        displayAddressButton.setAlignment(Pos.CENTER_RIGHT);
        displayAddressButton.setOnAction(event -> {
            displayAddressButton.setDisable(true);
            displayAddress();
        });
        displayAddressButton.managedProperty().bind(displayAddressButton.visibleProperty());
        displayAddressButton.setVisible(false);

        if(device.getFingerprint() != null && !device.getFingerprint().equals(keyDerivation.getMasterFingerprint())) {
            displayAddressButton.setDisable(true);
        }
    }

    private void createSignMessageButton() {
        signMessageButton = new Button("Sign Message");
        signMessageButton.setDefaultButton(true);
        signMessageButton.setAlignment(Pos.CENTER_RIGHT);
        signMessageButton.setOnAction(event -> {
            signMessageButton.setDisable(true);
            signMessage();
        });
        signMessageButton.managedProperty().bind(signMessageButton.visibleProperty());
        signMessageButton.setVisible(false);

        if(device.getFingerprint() != null && !device.getFingerprint().equals(keyDerivation.getMasterFingerprint())) {
            signMessageButton.setDisable(true);
        }
    }

    private void unlock(Device device) {
        if(device.getModel().requiresPinPrompt()) {
            promptPin();
        }
    }

    private Node getPinEntry() {
        VBox vBox = new VBox();
        vBox.setMaxHeight(120);
        vBox.setSpacing(42);
        pinField = (CustomPasswordField)TextFields.createClearablePasswordField();
        enterPinButton = new Button("Enter PIN");
        enterPinButton.setOnAction(event -> {
            enterPinButton.setDisable(true);
            sendPin(pinField.getText());
        });
        vBox.getChildren().addAll(pinField, enterPinButton);

        TilePane tilePane = new TilePane();
        tilePane.setPrefColumns(3);
        tilePane.setHgap(10);
        tilePane.setVgap(10);
        tilePane.setMaxWidth(150);
        tilePane.setMaxHeight(120);

        int[] digits = new int[] {7, 8, 9, 4, 5, 6, 1, 2, 3};
        for(int i = 0; i < digits.length; i++) {
            Button pinButton = new Button();
            Glyph circle = new Glyph(FontAwesome5.FONT_NAME, "CIRCLE");
            pinButton.setGraphic(circle);
            pinButton.setUserData(digits[i]);
            tilePane.getChildren().add(pinButton);
            pinButton.setOnAction(event -> {
                pinField.setText(pinField.getText() + pinButton.getUserData());
            });
        }

        HBox contentBox = new HBox();
        contentBox.setSpacing(50);
        contentBox.getChildren().add(tilePane);
        contentBox.getChildren().add(vBox);
        contentBox.setPadding(new Insets(10, 0, 10, 0));
        contentBox.setAlignment(Pos.TOP_CENTER);

        return contentBox;
    }

    private Node getPassphraseEntry() {
        CustomTextField passphraseField = (CustomTextField)TextFields.createClearableTextField();
        passphrase.bind(passphraseField.textProperty());
        HBox.setHgrow(passphraseField, Priority.ALWAYS);

        Button sendPassphraseButton = new Button("Send Passphrase");
        sendPassphraseButton.setOnAction(event -> {
            setExpanded(false);
            sendPassphrase(passphrase.get());
        });

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(passphraseField);
        contentBox.getChildren().add(sendPassphraseButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        Platform.runLater(passphraseField::requestFocus);

        return contentBox;
    }

    private void promptPin() {
        Hwi.PromptPinService promptPinService = new Hwi.PromptPinService(device);
        promptPinService.setOnSucceeded(workerStateEvent -> {
            Boolean result = promptPinService.getValue();
            if(result) {
                setContent(getPinEntry());
                setExpanded(true);
            } else {
                setError("Could not request PIN", null);
                unlockButton.setDisable(false);
            }
        });
        promptPinService.setOnFailed(workerStateEvent -> {
            setError(promptPinService.getException().getMessage(), null);
            unlockButton.setDisable(false);
        });
        promptPinService.start();
    }

    private void sendPin(String pin) {
        Hwi.SendPinService sendPinService = new Hwi.SendPinService(device, pin);
        sendPinService.setOnSucceeded(workerStateEvent -> {
            Boolean result = sendPinService.getValue();
            if(result) {
                device.setNeedsPinSent(false);
                setDefaultStatus();
                setExpanded(false);
                unlockButton.setVisible(false);

                if(device.getNeedsPassphraseSent()) {
                    setPassphraseButton.setVisible(true);
                    setPassphraseButton.setDisable(true);
                    setContent(getPassphraseEntry());
                    setExpanded(true);
                } else {
                    showOperationButton();
                }
            } else {
                setError("Incorrect PIN", null);
                unlockButton.setDisable(false);
                if(pinField != null) {
                    pinField.setText("");
                }
            }
        });
        sendPinService.setOnFailed(workerStateEvent -> {
            setError(sendPinService.getException().getMessage(), null);
            enterPinButton.setDisable(false);
        });
        setDescription("Unlocking...");
        showHideLink.setVisible(false);
        sendPinService.start();
    }

    private void sendPassphrase(String passphrase) {
        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(passphrase);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            List<Device> devices = enumerateService.getValue();
            for (Device freshDevice : devices) {
                if (device.getPath().equals(freshDevice.getPath()) && device.getModel().equals(freshDevice.getModel())) {
                    device.setFingerprint(freshDevice.getFingerprint());
                }
            }

            if(device.getFingerprint() != null) {
                setPassphraseButton.setVisible(false);
                setDescription("Passphrase sent");
                showOperationButton();
            } else {
                setError("Passphrase send failed", null);
                setPassphraseButton.setDisable(false);
                setPassphraseButton.setVisible(true);
            }
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            setError(enumerateService.getException().getMessage(), null);
            setPassphraseButton.setDisable(false);
            setPassphraseButton.setVisible(true);
        });
        enumerateService.start();
    }

    private void importKeystore(List<ChildNumber> derivation) {
        if(device.getFingerprint() == null) {
            Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(passphrase.get());
            enumerateService.setOnSucceeded(workerStateEvent -> {
                List<Device> devices = enumerateService.getValue();
                for (Device freshDevice : devices) {
                    if (device.getPath().equals(freshDevice.getPath()) && device.getModel().equals(freshDevice.getModel())) {
                        device.setFingerprint(freshDevice.getFingerprint());
                    }
                }

                importXpub(derivation);
            });
            enumerateService.setOnFailed(workerStateEvent -> {
                setError(enumerateService.getException().getMessage(), null);
                importButton.setDisable(false);
            });
            enumerateService.start();
        } else {
            importXpub(derivation);
        }
    }

    private void importXpub(List<ChildNumber> derivation) {
        String derivationPath = KeyDerivation.writePath(derivation);

        Hwi.GetXpubService getXpubService = new Hwi.GetXpubService(device, passphrase.get(), derivationPath);
        getXpubService.setOnSucceeded(workerStateEvent -> {
            String xpub = getXpubService.getValue();

            Keystore keystore = new Keystore();
            keystore.setLabel(device.getModel().toDisplayString());
            keystore.setSource(KeystoreSource.HW_USB);
            keystore.setWalletModel(device.getModel());
            keystore.setKeyDerivation(new KeyDerivation(device.getFingerprint(), derivationPath));
            keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(xpub));

            EventManager.get().post(new KeystoreImportEvent(keystore));
        });
        getXpubService.setOnFailed(workerStateEvent -> {
            setError("Could not retrieve xpub", getXpubService.getException().getMessage());
            importButton.setDisable(false);
        });
        setDescription("Importing...");
        showHideLink.setVisible(false);
        getXpubService.start();
    }

    private void sign() {
        Hwi.SignPSBTService signPSBTService = new Hwi.SignPSBTService(device, passphrase.get(), psbt);
        signPSBTService.setOnSucceeded(workerStateEvent -> {
            PSBT signedPsbt = signPSBTService.getValue();
            EventManager.get().post(new PSBTSignedEvent(psbt, signedPsbt));
        });
        signPSBTService.setOnFailed(workerStateEvent -> {
            setError("Signing Error", signPSBTService.getException().getMessage());
            log.error("Signing Error: " + signPSBTService.getException().getMessage());
            signButton.setDisable(false);
        });
        setDescription("Signing...");
        showHideLink.setVisible(false);
        signPSBTService.start();
    }

    private void displayAddress() {
        Hwi.DisplayAddressService displayAddressService = new Hwi.DisplayAddressService(device, passphrase.get(), wallet.getScriptType(), keyDerivation.getDerivationPath());
        displayAddressService.setOnSucceeded(successEvent -> {
            String address = displayAddressService.getValue();
            EventManager.get().post(new AddressDisplayedEvent(address));
        });
        displayAddressService.setOnFailed(failedEvent -> {
            setError("Could not display address", displayAddressService.getException().getMessage());
            displayAddressButton.setDisable(false);
        });
        setDescription("Check device for address");
        displayAddressService.start();
    }

    private void signMessage() {
        Hwi.SignMessageService signMessageService = new Hwi.SignMessageService(device, passphrase.get(), message, keyDerivation.getDerivationPath());
        signMessageService.setOnSucceeded(successEvent -> {
            String signature = signMessageService.getValue();
            EventManager.get().post(new MessageSignedEvent(wallet, signature));
        });
        signMessageService.setOnFailed(failedEvent -> {
            setError("Could not sign message", signMessageService.getException().getMessage());
            signMessageButton.setDisable(false);
        });
        setDescription("Signing message...");
        signMessageService.start();
    }

    private void showOperationButton() {
        if(deviceOperation.equals(DeviceOperation.IMPORT)) {
            importButton.setVisible(true);
            showHideLink.setText("Show derivation...");
            showHideLink.setVisible(true);
            setContent(getDerivationEntry(wallet.getScriptType().getDefaultDerivation()));
        } else if(deviceOperation.equals(DeviceOperation.SIGN)) {
            signButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.DISPLAY_ADDRESS)) {
            displayAddressButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.SIGN_MESSAGE)) {
            signMessageButton.setVisible(true);
            showHideLink.setVisible(false);
        }
    }

    private Node getDerivationEntry(List<ChildNumber> derivation) {
        TextField derivationField = new TextField();
        derivationField.setPromptText("Derivation path");
        derivationField.setText(KeyDerivation.writePath(derivation));
        HBox.setHgrow(derivationField, Priority.ALWAYS);

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.registerValidator(derivationField, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());

        Button importDerivationButton = new Button("Import");
        importDerivationButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            List<ChildNumber> importDerivation = KeyDerivation.parsePath(derivationField.getText());
            importXpub(importDerivation);
        });

        derivationField.textProperty().addListener((observable, oldValue, newValue) -> {
            importDerivationButton.setDisable(newValue.isEmpty() || !KeyDerivation.isValid(newValue));
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

    public enum DeviceOperation {
        IMPORT, SIGN, DISPLAY_ADDRESS, SIGN_MESSAGE;
    }
}
