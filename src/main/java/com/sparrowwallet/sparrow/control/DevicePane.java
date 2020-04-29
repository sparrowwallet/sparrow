package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.ExtendedPublicKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.external.Device;
import com.sparrowwallet.sparrow.external.Hwi;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;

import java.util.List;

public class DevicePane extends TitledPane {
    private final DeviceAccordion deviceAccordion;
    private final Wallet wallet;
    private final Device device;

    private Label mainLabel;
    private Label statusLabel;
    private CustomPasswordField pinField;
    private CustomTextField passphraseField;
    private Button unlockButton;
    private Button enterPinButton;
    private Button setPassphraseButton;
    private SplitMenuButton importButton;

    private final SimpleStringProperty status = new SimpleStringProperty("");
    private final SimpleStringProperty passphrase = new SimpleStringProperty("");

    public DevicePane(DeviceAccordion deviceAccordion, Wallet wallet, Device device) {
        super();
        this.deviceAccordion = deviceAccordion;
        this.wallet = wallet;
        this.device = device;

        setPadding(Insets.EMPTY);

        setGraphic(getTitle());
        getStyleClass().add("devicepane");
        setDefaultStatus();

        Platform.runLater(() -> {
            Node arrow = this.lookup(".arrow");
            if(arrow != null) {
                arrow.setVisible(false);
                arrow.setManaged(false);
            }
        });
    }

    private void setDefaultStatus() {
        setStatus(device.getNeedsPinSent() ? "Locked" : device.getNeedsPassphraseSent() ? "Passphrase Required" : "Unlocked");
    }

    private Node getTitle() {
        HBox listItem = new HBox();
        listItem.setPadding(new Insets(10, 20, 10, 10));
        listItem.setSpacing(10);

        HBox imageBox = new HBox();
        imageBox.setMinWidth(50);
        imageBox.setMinHeight(50);
        listItem.getChildren().add(imageBox);

        Image image = new Image("image/" + device.getType() + ".png", 50, 50, true, true);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setImage(image);
            imageBox.getChildren().add(imageView);
        }

        VBox labelsBox = new VBox();
        labelsBox.setSpacing(5);
        labelsBox.setAlignment(Pos.CENTER_LEFT);
        this.mainLabel = new Label();
        mainLabel.setText(device.getModel().toDisplayString());
        mainLabel.getStyleClass().add("main-label");
        labelsBox.getChildren().add(mainLabel);

        this.statusLabel = new Label();
        statusLabel.textProperty().bind(status);

        labelsBox.getChildren().add(statusLabel);
        statusLabel.getStyleClass().add("status-label");
        listItem.getChildren().add(labelsBox);
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        createUnlockButton();
        createSetPassphraseButton();
        createImportButton();

        if (device.getNeedsPinSent() != null && device.getNeedsPinSent()) {
            unlockButton.setVisible(true);
        } else if(device.getNeedsPassphraseSent() != null && device.getNeedsPassphraseSent()) {
            setPassphraseButton.setVisible(true);
        } else {
            showOperationButton();
        }

        buttonBox.getChildren().addAll(unlockButton, setPassphraseButton, importButton);
        listItem.getChildren().add(buttonBox);

        this.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
            listItem.setPrefWidth(newValue.getWidth());
        });

        return listItem;
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
        for(int i = 0; i < accounts.length; i++) {
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

    private void unlock(Device device) {
        if(device.getModel().equals(WalletModel.TREZOR_1)) {
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
        passphraseField = (CustomTextField)TextFields.createClearableTextField();
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
                setErrorStatus("Could not request PIN");
                unlockButton.setDisable(false);
            }
        });
        promptPinService.setOnFailed(workerStateEvent -> {
            setErrorStatus(promptPinService.getException().getMessage());
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
                setErrorStatus("Incorrect PIN");
                enterPinButton.setDisable(false);
                if(pinField != null) {
                    pinField.setText("");
                }
            }
        });
        sendPinService.setOnFailed(workerStateEvent -> {
            setErrorStatus(sendPinService.getException().getMessage());
            enterPinButton.setDisable(false);
        });
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
                device.setNeedsPassphraseSent(false);
                setDefaultStatus();
                showOperationButton();
            } else {
                setErrorStatus("Passphrase send failed");
                setPassphraseButton.setDisable(false);
                setPassphraseButton.setVisible(true);
            }
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            setErrorStatus(enumerateService.getException().getMessage());
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
                setErrorStatus(enumerateService.getException().getMessage());
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
            keystore.setLabel(device.getModel().toDisplayString() + " " + device.getFingerprint().toUpperCase());
            keystore.setSource(KeystoreSource.HW_USB);
            keystore.setWalletModel(device.getModel());
            keystore.setKeyDerivation(new KeyDerivation(device.getFingerprint(), derivationPath));
            keystore.setExtendedPublicKey(ExtendedPublicKey.fromDescriptor(xpub));

            EventManager.get().post(new KeystoreImportEvent(keystore));
        });
        getXpubService.setOnFailed(workerStateEvent -> {
            setErrorStatus(getXpubService.getException().getMessage());
            importButton.setDisable(false);
        });
        getXpubService.start();
    }

    private void setStatus(String statusMessage) {
        statusLabel.getStyleClass().remove("status-error");
        status.setValue(statusMessage);
    }

    private void setErrorStatus(String statusMessage) {
        statusLabel.getStyleClass().add("status-error");
        status.setValue(statusMessage);
    }

    private void showOperationButton() {
        if(deviceAccordion.getDeviceOperation().equals(DeviceAccordion.DeviceOperation.IMPORT)) {
            importButton.setVisible(true);
        } else {
            //TODO: Support further device operations such as signing
        }
    }
}
