package com.sparrowwallet.sparrow.control;

import com.google.common.base.Throwables;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.CardApi;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.CardAuthorizationException;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class DevicePane extends TitledDescriptionPane {
    private static final Logger log = LoggerFactory.getLogger(DevicePane.class);

    private final DeviceOperation deviceOperation;
    private final Wallet wallet;
    private final PSBT psbt;
    private final OutputDescriptor outputDescriptor;
    private final KeyDerivation keyDerivation;
    private final String message;
    private final List<StandardAccount> availableAccounts;
    private final Device device;

    private CustomPasswordField pinField;
    private Button unlockButton;
    private Button enterPinButton;
    private Button setPassphraseButton;
    private ButtonBase importButton;
    private Button signButton;
    private Button displayAddressButton;
    private Button signMessageButton;
    private Button discoverKeystoresButton;
    private Button unsealButton;

    private final SimpleStringProperty passphrase = new SimpleStringProperty("");
    private final SimpleStringProperty pin = new SimpleStringProperty("");
    private final StringProperty messageProperty = new SimpleStringProperty("");

    private boolean defaultDevice;

    public DevicePane(Wallet wallet, Device device, boolean defaultDevice, KeyDerivation requiredDerivation) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.IMPORT;
        this.wallet = wallet;
        this.psbt = null;
        this.outputDescriptor = null;
        this.keyDerivation = requiredDerivation;
        this.message = null;
        this.availableAccounts = null;
        this.device = device;
        this.defaultDevice = defaultDevice;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createImportButton();

        initialise(device);

        messageProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> setDescription(newValue));
        });

        buttonBox.getChildren().addAll(setPassphraseButton, importButton);
    }

    public DevicePane(Wallet wallet, PSBT psbt, Device device, boolean defaultDevice) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.SIGN;
        this.wallet = wallet;
        this.psbt = psbt;
        this.outputDescriptor = null;
        this.keyDerivation = null;
        this.message = null;
        this.availableAccounts = null;
        this.device = device;
        this.defaultDevice = defaultDevice;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createSignButton();

        initialise(device);

        messageProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> setDescription(newValue));
        });

        buttonBox.getChildren().addAll(setPassphraseButton, signButton);
    }

    public DevicePane(Wallet wallet, OutputDescriptor outputDescriptor, Device device, boolean defaultDevice) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.DISPLAY_ADDRESS;
        this.wallet = wallet;
        this.psbt = null;
        this.outputDescriptor = outputDescriptor;
        this.keyDerivation = null;
        this.message = null;
        this.availableAccounts = null;
        this.device = device;
        this.defaultDevice = defaultDevice;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createDisplayAddressButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, displayAddressButton);
    }

    public DevicePane(Wallet wallet, String message, KeyDerivation keyDerivation, Device device, boolean defaultDevice) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.SIGN_MESSAGE;
        this.wallet = wallet;
        this.psbt = null;
        this.outputDescriptor = null;
        this.keyDerivation = keyDerivation;
        this.message = message;
        this.availableAccounts = null;
        this.device = device;
        this.defaultDevice = defaultDevice;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createSignMessageButton();

        initialise(device);

        messageProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> setDescription(newValue));
        });

        buttonBox.getChildren().addAll(setPassphraseButton, signMessageButton);
    }

    public DevicePane(Wallet wallet, List<StandardAccount> availableAccounts, Device device, boolean defaultDevice) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.DISCOVER_KEYSTORES;
        this.wallet = wallet;
        this.psbt = null;
        this.outputDescriptor = null;
        this.keyDerivation = null;
        this.message = null;
        this.device = device;
        this.defaultDevice = defaultDevice;
        this.availableAccounts = availableAccounts;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createSetPassphraseButton();
        createDiscoverKeystoresButton();

        initialise(device);

        buttonBox.getChildren().addAll(setPassphraseButton, discoverKeystoresButton);
    }

    public DevicePane(Device device, boolean defaultDevice) {
        super(device.getModel().toDisplayString(), "", "", "image/" + device.getType() + ".png");
        this.deviceOperation = DeviceOperation.UNSEAL;
        this.wallet = null;
        this.psbt = null;
        this.outputDescriptor = null;
        this.keyDerivation = null;
        this.message = null;
        this.device = device;
        this.defaultDevice = defaultDevice;
        this.availableAccounts = null;

        setDefaultStatus();
        showHideLink.setVisible(false);

        createUnsealButton();

        initialise(device);

        messageProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> setDescription(newValue));
        });

        buttonBox.getChildren().add(unsealButton);
    }

    private void initialise(Device device) {
        if(device.isNeedsPinSent()) {
            unlockButton.setDefaultButton(defaultDevice);
            unlockButton.setVisible(true);
        } else if(device.isNeedsPassphraseSent()) {
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
        setDescription(device.isNeedsPinSent() ? "Locked" : device.isNeedsPassphraseSent() ? "Passphrase Required" : device.isCard() ? "Place card on reader" : "Unlocked");
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
        importButton = keyDerivation == null ? new SplitMenuButton() : new Button();
        importButton.setAlignment(Pos.CENTER_RIGHT);
        importButton.setText("Import Keystore");
        importButton.setOnAction(event -> {
            importButton.setDisable(true);
            List<ChildNumber> defaultDerivation = wallet.getScriptType() == null ? ScriptType.P2WPKH.getDefaultDerivation() : wallet.getScriptType().getDefaultDerivation();
            importKeystore(keyDerivation == null ? defaultDerivation : keyDerivation.getDerivation());
        });

        if(importButton instanceof SplitMenuButton importMenuButton) {
            if(wallet.getScriptType() == null) {
                ScriptType[] scriptTypes = new ScriptType[] {ScriptType.P2WPKH, ScriptType.P2SH_P2WPKH, ScriptType.P2PKH};
                for(ScriptType scriptType : scriptTypes) {
                    MenuItem item = new MenuItem(scriptType.getDescription());
                    final List<ChildNumber> derivation = scriptType.getDefaultDerivation();
                    item.setOnAction(event -> {
                        importMenuButton.setDisable(true);
                        importKeystore(derivation);
                    });
                    importMenuButton.getItems().add(item);
                }
            } else {
                String[] accounts = new String[] {"Default Account #0", "Account #1", "Account #2", "Account #3", "Account #4", "Account #5", "Account #6", "Account #7", "Account #8", "Account #9"};
                int scriptAccountsLength = ScriptType.P2SH.equals(wallet.getScriptType()) ? 1 : accounts.length;
                for(int i = 0; i < scriptAccountsLength; i++) {
                    MenuItem item = new MenuItem(accounts[i]);
                    final List<ChildNumber> derivation = wallet.getScriptType().getDefaultDerivation(i);
                    item.setOnAction(event -> {
                        importMenuButton.setDisable(true);
                        importKeystore(derivation);
                    });
                    importMenuButton.getItems().add(item);
                }
            }
        }
        importButton.managedProperty().bind(importButton.visibleProperty());
        importButton.setVisible(false);
    }

    private void createSignButton() {
        signButton = new Button("Sign");
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
        displayAddressButton.setAlignment(Pos.CENTER_RIGHT);
        displayAddressButton.setOnAction(event -> {
            displayAddressButton.setDisable(true);
            displayAddress();
        });
        displayAddressButton.managedProperty().bind(displayAddressButton.visibleProperty());
        displayAddressButton.setVisible(false);

        List<String> fingerprints = outputDescriptor.getExtendedPublicKeys().stream().map(extKey -> outputDescriptor.getKeyDerivation(extKey).getMasterFingerprint()).collect(Collectors.toList());
        if(device.getFingerprint() != null && !fingerprints.contains(device.getFingerprint())) {
            displayAddressButton.setDisable(true);
        }
    }

    private void createSignMessageButton() {
        signMessageButton = new Button("Sign Message");
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

    private void createDiscoverKeystoresButton() {
        discoverKeystoresButton = new Button("Discover");
        discoverKeystoresButton.setAlignment(Pos.CENTER_RIGHT);
        discoverKeystoresButton.setOnAction(event -> {
            discoverKeystoresButton.setDisable(true);
            discoverKeystores();
        });
        discoverKeystoresButton.managedProperty().bind(discoverKeystoresButton.visibleProperty());
        discoverKeystoresButton.setVisible(false);
    }

    private void createUnsealButton() {
        unsealButton = new Button("Unseal");
        unsealButton.setAlignment(Pos.CENTER_RIGHT);
        unsealButton.setOnAction(event -> {
            unsealButton.setDisable(true);
            unseal();
        });
        unsealButton.managedProperty().bind(unsealButton.visibleProperty());
        unsealButton.setVisible(false);
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
        pinField = new ViewPasswordField();
        Platform.runLater(() -> pinField.requestFocus());
        enterPinButton = new Button("Enter PIN");
        enterPinButton.setDefaultButton(true);
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
        CustomPasswordField passphraseField = new ViewPasswordField();
        passphrase.bind(passphraseField.textProperty());
        HBox.setHgrow(passphraseField, Priority.ALWAYS);
        passphraseField.setOnAction(event -> {
            setExpanded(false);
            setDescription("Confirm passphrase on device...");
            sendPassphrase(passphrase.get());
        });

        SplitMenuButton sendPassphraseButton = new SplitMenuButton();
        sendPassphraseButton.setText("Send Passphrase");
        sendPassphraseButton.getStyleClass().add("default-button");
        sendPassphraseButton.setOnAction(event -> {
            setExpanded(false);
            setDescription("Confirm passphrase on device...");
            sendPassphrase(passphrase.get());
        });

        MenuItem removePassphrase = new MenuItem("Toggle Passphrase Off");
        removePassphrase.setOnAction(event -> {
            setExpanded(false);
            setDescription("Toggling passphrase off, check device...");
            togglePassphraseOff();
        });
        sendPassphraseButton.getItems().add(removePassphrase);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(passphraseField);
        contentBox.getChildren().add(sendPassphraseButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        Platform.runLater(passphraseField::requestFocus);

        return contentBox;
    }

    private Node getTogglePassphraseOn() {
        CopyableLabel label = new CopyableLabel("Passphrase is currently disabled");
        HBox.setHgrow(label, Priority.ALWAYS);

        Button togglePassphraseOn = new Button("Toggle Passphrase On");
        togglePassphraseOn.setOnAction(event -> {
            setExpanded(false);
            hideButtons(importButton, signButton, displayAddressButton, signMessageButton);
            setDescription("Toggling passphrase on, check device...");
            togglePassphraseOn();
        });

        HBox contentBox = new HBox();
        contentBox.setSpacing(20);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(label, togglePassphraseOn);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        return contentBox;
    }

    private void hideButtons(Node... buttons) {
        for(Node button : buttons) {
            if(button != null) {
                button.setVisible(false);
            }
        }
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
            setError("Error", promptPinService.getException().getMessage());
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

                if(device.isNeedsPassphraseSent()) {
                    setPassphraseButton.setVisible(true);
                    setPassphraseButton.setDisable(true);
                    setContent(getPassphraseEntry());
                    setExpanded(true);
                } else {
                    showOperationButton();
                    if(!deviceOperation.equals(DeviceOperation.IMPORT)) {
                        setContent(getTogglePassphraseOn());
                    }
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
            setError("Error", sendPinService.getException().getMessage());
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
            setError("Error", enumerateService.getException().getMessage());
            setPassphraseButton.setDisable(false);
            setPassphraseButton.setVisible(true);
        });
        enumerateService.start();
    }

    private void togglePassphraseOff() {
        Hwi.TogglePassphraseService togglePassphraseService = new Hwi.TogglePassphraseService(device);
        togglePassphraseService.setOnSucceeded(workerStateEvent -> {
            device.setNeedsPassphraseSent(false);
            setPassphraseButton.setVisible(false);
            setDescription("Unlocked");
            showOperationButton();
        });
        togglePassphraseService.setOnFailed(workerStateEvent -> {
            setError("Error", togglePassphraseService.getException().getMessage());
        });
        togglePassphraseService.start();
    }

    private void togglePassphraseOn() {
        Hwi.TogglePassphraseService togglePassphraseService = new Hwi.TogglePassphraseService(device);
        togglePassphraseService.setOnSucceeded(workerStateEvent -> {
            device.setNeedsPassphraseSent(true);
            setPassphraseButton.setVisible(true);
            setPassphraseButton.setDisable(true);
            setDescription("Enter passphrase");
            setContent(getPassphraseEntry());
            setExpanded(true);
        });
        togglePassphraseService.setOnFailed(workerStateEvent -> {
            setError("Error", togglePassphraseService.getException().getMessage());
        });
        togglePassphraseService.start();
    }

    private void importKeystore(List<ChildNumber> derivation) {
        if(device.isCard()) {
            try {
                CardApi cardApi = CardApi.getCardApi(device.getModel(), pin.get());
                if(!cardApi.isInitialized()) {
                    setDescription("Card not initialized");
                    setContent(getCardInitializationPanel(cardApi));
                    showHideLink.setVisible(false);
                    setExpanded(true);
                    return;
                }

                Service<Keystore> importService = cardApi.getImportService(derivation, messageProperty);
                handleCardOperation(importService, importButton, "Import", event -> {
                    importKeystore(derivation, importService.getValue());
                });
            } catch(Exception e) {
                log.error("Import Error: " + e.getMessage(), e);
                setError("Import Error", e.getMessage());
                importButton.setDisable(false);
            }
        } else if(device.getFingerprint() == null) {
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
                setError("Error", enumerateService.getException().getMessage());
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

            try {
                Keystore keystore = new Keystore();
                keystore.setLabel(device.getModel().toDisplayString());
                keystore.setSource(KeystoreSource.HW_USB);
                keystore.setWalletModel(device.getModel());
                keystore.setKeyDerivation(new KeyDerivation(device.getFingerprint(), derivationPath));
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(xpub));

                importKeystore(derivation, keystore);
            } catch(Exception e) {
                setError("Could not retrieve xpub", e.getMessage());
            }
        });
        getXpubService.setOnFailed(workerStateEvent -> {
            setError("Could not retrieve xpub", getXpubService.getException().getMessage());
            importButton.setDisable(false);
        });
        setDescription("Importing...");
        showHideLink.setVisible(false);
        getXpubService.start();
    }

    private void importKeystore(List<ChildNumber> derivation, Keystore keystore) {
        if(wallet.getScriptType() == null) {
            ScriptType scriptType = Arrays.stream(ScriptType.ADDRESSABLE_TYPES).filter(type -> type.getDefaultDerivation().get(0).equals(derivation.get(0))).findFirst().orElse(ScriptType.P2PKH);
            wallet.setName(device.getModel().toDisplayString());
            wallet.setPolicyType(PolicyType.SINGLE);
            wallet.setScriptType(scriptType);
            wallet.getKeystores().add(keystore);
            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), null));

            EventManager.get().post(new WalletImportEvent(wallet));
        } else {
            EventManager.get().post(new KeystoreImportEvent(keystore));
        }
    }

    private void sign() {
        if(device.isCard()) {
            try {
                CardApi cardApi = CardApi.getCardApi(device.getModel(), pin.get());
                Service<PSBT> signService = cardApi.getSignService(wallet, psbt, messageProperty);
                handleCardOperation(signService, signButton, "Signing", event -> {
                    EventManager.get().post(new PSBTSignedEvent(psbt, signService.getValue()));
                });
            } catch(Exception e) {
                log.error("Signing Error: " + e.getMessage(), e);
                setError("Signing Error", e.getMessage());
                signButton.setDisable(false);
            }
        } else {
            Hwi.SignPSBTService signPSBTService = new Hwi.SignPSBTService(device, passphrase.get(), psbt);
            signPSBTService.setOnSucceeded(workerStateEvent -> {
                PSBT signedPsbt = signPSBTService.getValue();
                EventManager.get().post(new PSBTSignedEvent(psbt, signedPsbt));
            });
            signPSBTService.setOnFailed(workerStateEvent -> {
                setError("Signing Error", signPSBTService.getException().getMessage());
                log.error("Signing Error: " + signPSBTService.getException().getMessage(), signPSBTService.getException());
                signButton.setDisable(false);
            });
            setDescription("Signing...");
            showHideLink.setVisible(false);
            signPSBTService.start();
        }
    }

    private void handleCardOperation(Service<?> service, ButtonBase operationButton, String operationDescription, EventHandler<WorkerStateEvent> successHandler) {
        if(pin.get().length() < 6) {
            setDescription(pin.get().isEmpty() ? "Enter PIN code" : "PIN code too short");
            setContent(getCardPinEntry(operationButton));
            showHideLink.setVisible(false);
            setExpanded(true);
            operationButton.setDisable(false);
            return;
        }

        service.setOnSucceeded(successHandler);
        service.setOnFailed(event -> {
            Throwable rootCause = Throwables.getRootCause(event.getSource().getException());
            if(rootCause instanceof CardAuthorizationException) {
                setError(rootCause.getMessage(), null);
                setContent(getCardPinEntry(operationButton));
            } else {
                log.error(operationDescription + " Error: " + rootCause.getMessage(), event.getSource().getException());
                setError(operationDescription + " Error", rootCause.getMessage());
            }
            operationButton.setDisable(false);
        });
        service.start();
    }

    private void displayAddress() {
        Hwi.DisplayAddressService displayAddressService = new Hwi.DisplayAddressService(device, passphrase.get(), wallet.getScriptType(), outputDescriptor);
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
        if(device.isCard()) {
            try {
                CardApi cardApi = CardApi.getCardApi(device.getModel(), pin.get());
                Service<String> signMessageService = cardApi.getSignMessageService(message, wallet.getScriptType(), keyDerivation.getDerivation(), messageProperty);
                handleCardOperation(signMessageService, signMessageButton, "Signing", event -> {
                    String signature = signMessageService.getValue();
                    EventManager.get().post(new MessageSignedEvent(wallet, signature));
                });
            } catch(Exception e) {
                log.error("Signing Error: " + e.getMessage(), e);
                setError("Signing Error", e.getMessage());
                signButton.setDisable(false);
            }
        } else {
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
    }

    private void discoverKeystores() {
        if(wallet.getKeystores().size() != 1) {
            setError("Could not discover keystores", "Only single signature wallets are supported for keystore discovery");
            return;
        }

        String masterFingerprint = wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint();

        Wallet copyWallet = wallet.copy();
        Map<StandardAccount, String> accountDerivationPaths = new LinkedHashMap<>();
        for(StandardAccount availableAccount : availableAccounts) {
            Wallet availableWallet = copyWallet.addChildWallet(availableAccount);
            Keystore availableKeystore = availableWallet.getKeystores().get(0);
            String derivationPath = availableKeystore.getKeyDerivation().getDerivationPath();
            accountDerivationPaths.put(availableAccount, derivationPath);
        }

        Map<StandardAccount, Keystore> importedKeystores = new LinkedHashMap<>();
        Hwi.GetXpubsService getXpubsService = new Hwi.GetXpubsService(device, passphrase.get(), accountDerivationPaths);
        getXpubsService.setOnSucceeded(workerStateEvent -> {
            Map<StandardAccount, String> accountXpubs = getXpubsService.getValue();

            for(Map.Entry<StandardAccount, String> entry : accountXpubs.entrySet()) {
                try {
                    Keystore keystore = new Keystore();
                    keystore.setLabel(device.getModel().toDisplayString());
                    keystore.setSource(KeystoreSource.HW_USB);
                    keystore.setWalletModel(device.getModel());
                    keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, accountDerivationPaths.get(entry.getKey())));
                    keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(entry.getValue()));
                    importedKeystores.put(entry.getKey(), keystore);
                } catch(Exception e) {
                    setError("Could not retrieve xpub", e.getMessage());
                }
            }

            ElectrumServer.AccountDiscoveryService accountDiscoveryService = new ElectrumServer.AccountDiscoveryService(wallet, importedKeystores);
            accountDiscoveryService.setOnSucceeded(event -> {
                importedKeystores.keySet().retainAll(accountDiscoveryService.getValue());
                EventManager.get().post(new KeystoresDiscoveredEvent(importedKeystores));
            });
            accountDiscoveryService.setOnFailed(event -> {
                log.error("Failed to discover accounts", event.getSource().getException());
                setError("Failed to discover accounts", event.getSource().getException().getMessage());
                discoverKeystoresButton.setDisable(false);
            });
            accountDiscoveryService.start();
        });
        getXpubsService.setOnFailed(workerStateEvent -> {
            setError("Could not retrieve xpub", getXpubsService.getException().getMessage());
            discoverKeystoresButton.setDisable(false);
        });
        setDescription("Discovering...");
        showHideLink.setVisible(false);
        getXpubsService.start();
    }

    private void unseal() {
        if(device.isCard()) {
            try {
                CardApi cardApi = CardApi.getCardApi(device.getModel(), pin.get());
                Service<ECKey> unsealService = cardApi.getUnsealService(messageProperty);
                handleCardOperation(unsealService, unsealButton, "Unseal", event -> {
                    EventManager.get().post(new DeviceUnsealedEvent(unsealService.getValue(), cardApi.getDefaultScriptType()));
                });
            } catch(Exception e) {
                log.error("Unseal Error: " + e.getMessage(), e);
                setError("Unseal Error", e.getMessage());
                unsealButton.setDisable(false);
            }
        }
    }

    private void showOperationButton() {
        if(deviceOperation.equals(DeviceOperation.IMPORT)) {
            if(defaultDevice) {
                importButton.getStyleClass().add("default-button");
            }
            importButton.setVisible(true);
            showHideLink.setText("Show derivation...");
            showHideLink.setVisible(true);
            List<ChildNumber> defaultDerivation = wallet.getScriptType() == null ? ScriptType.P2WPKH.getDefaultDerivation() : wallet.getScriptType().getDefaultDerivation();
            setContent(getDerivationEntry(keyDerivation == null ? defaultDerivation : keyDerivation.getDerivation()));
        } else if(deviceOperation.equals(DeviceOperation.SIGN)) {
            signButton.setDefaultButton(defaultDevice);
            signButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.DISPLAY_ADDRESS)) {
            displayAddressButton.setDefaultButton(defaultDevice);
            displayAddressButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.SIGN_MESSAGE)) {
            signMessageButton.setDefaultButton(defaultDevice);
            signMessageButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.DISCOVER_KEYSTORES)) {
            discoverKeystoresButton.setDefaultButton(defaultDevice);
            discoverKeystoresButton.setVisible(true);
            showHideLink.setVisible(false);
        } else if(deviceOperation.equals(DeviceOperation.UNSEAL)) {
            unsealButton.setDefaultButton(defaultDevice);
            unsealButton.setVisible(true);
            showHideLink.setVisible(false);
        }
    }

    private Node getDerivationEntry(List<ChildNumber> derivation) {
        TextField derivationField = new TextField();
        derivationField.setPromptText("Derivation path");
        derivationField.setText(KeyDerivation.writePath(derivation));
        derivationField.setDisable(device.isCard() || keyDerivation != null);
        HBox.setHgrow(derivationField, Priority.ALWAYS);

        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.registerValidator(derivationField, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid derivation", !KeyDerivation.isValid(newValue))
        ));

        Button importDerivationButton = new Button("Import Custom Derivation");
        importDerivationButton.setDisable(true);
        importDerivationButton.setOnAction(event -> {
            showHideLink.setVisible(true);
            setExpanded(false);
            List<ChildNumber> importDerivation = KeyDerivation.parsePath(derivationField.getText());
            importXpub(importDerivation);
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

    private Node getCardInitializationPanel(CardApi cardApi) {
        VBox initTypeBox = new VBox(5);
        RadioButton automatic = new RadioButton("Automatic (Recommended)");
        RadioButton advanced = new RadioButton("Advanced");
        TextField entropy = new TextField();
        entropy.setPromptText("Enter input for chain code");
        entropy.setDisable(true);

        ToggleGroup toggleGroup = new ToggleGroup();
        automatic.setToggleGroup(toggleGroup);
        advanced.setToggleGroup(toggleGroup);
        automatic.setSelected(true);
        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            entropy.setDisable(newValue == automatic);
        });

        initTypeBox.getChildren().addAll(automatic, advanced, entropy);

        Button initializeButton = new Button("Initialize");
        initializeButton.setDefaultButton(true);
        initializeButton.setOnAction(event -> {
            byte[] chainCode = toggleGroup.getSelectedToggle() == automatic ? null : Sha256Hash.hashTwice(entropy.getText().getBytes(StandardCharsets.UTF_8));
            Service<Void> cardInitializationService = cardApi.getInitializationService(chainCode);
            cardInitializationService.setOnSucceeded(event1 -> {
                AppServices.showSuccessDialog("Card Initialized", "The card was successfully initialized.\n\nYou will now need to enter the PIN code found on the back. You can change the PIN code once it has been imported.");
                setDescription("Enter PIN code");
                setContent(getCardPinEntry(importButton));
                importButton.setDisable(false);
                setExpanded(true);
            });
            cardInitializationService.setOnFailed(event1 -> {
                Throwable e = event1.getSource().getException();
                log.error("Error initializing card", e);
                AppServices.showErrorDialog("Card Initialization Failed", "The card was not initialized.\n\n" + e.getMessage());
            });
            cardInitializationService.start();
        });

        HBox contentBox = new HBox(20);
        contentBox.getChildren().addAll(initTypeBox, initializeButton);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        HBox.setHgrow(initTypeBox, Priority.ALWAYS);

        return contentBox;
    }

    private Node getCardPinEntry(ButtonBase operationButton) {
        VBox vBox = new VBox();

        CustomPasswordField pinField = new ViewPasswordField();
        pinField.setPromptText("PIN Code");
        if(operationButton instanceof Button defaultButton) {
            defaultButton.setDefaultButton(true);
        }
        pin.bind(pinField.textProperty());
        HBox.setHgrow(pinField, Priority.ALWAYS);
        Platform.runLater(pinField::requestFocus);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_RIGHT);
        contentBox.setSpacing(20);
        contentBox.getChildren().add(pinField);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefHeight(50);

        vBox.getChildren().add(contentBox);

        return vBox;
    }

    public Device getDevice() {
        return device;
    }

    public enum DeviceOperation {
        IMPORT, SIGN, DISPLAY_ADDRESS, SIGN_MESSAGE, DISCOVER_KEYSTORES, UNSEAL;
    }
}
