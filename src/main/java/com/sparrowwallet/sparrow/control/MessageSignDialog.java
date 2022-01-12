package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.OpenWalletsEvent;
import com.sparrowwallet.sparrow.event.RequestOpenWalletsEvent;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MessageSignDialog extends Dialog<ButtonBar.ButtonData> {
    private static final Logger log = LoggerFactory.getLogger(MessageSignDialog.class);

    private final TextField address;
    private final TextArea message;
    private final TextArea signature;
    private final ToggleGroup formatGroup;
    private final ToggleButton formatTrezor;
    private final ToggleButton formatElectrum;
    private final Wallet wallet;
    private WalletNode walletNode;
    private boolean electrumSignatureFormat;
    private boolean closed;

    /**
     * Verification only constructor
     */
    public MessageSignDialog() {
        this(null, null);
    }

    /**
     * Sign and verify with user entered address
     *
     * @param wallet Wallet to sign with
     */
    public MessageSignDialog(Wallet wallet) {
        this(wallet, null);
    }

    /**
     * Sign and verify with preset address
     *
     * @param wallet Wallet to sign with
     * @param walletNode Wallet node to derive address from
     */
    public MessageSignDialog(Wallet wallet, WalletNode walletNode) {
        this(wallet, walletNode, null, null);
    }

    /**
     * Sign and verify with preset address, and supplied title, message and dialog buttons
     *
     * @param wallet Wallet to sign with
     * @param walletNode Wallet node to derive address from
     * @param title Header text of dialog
     * @param msg Message to sign (all fields will be made uneditable)
     * @param buttons The dialog buttons to display. If one contains the text "sign" it will trigger the signing process
     */
    public MessageSignDialog(Wallet wallet, WalletNode walletNode, String title, String msg, ButtonType... buttons) {
        if(wallet != null) {
            if(wallet.getKeystores().size() != 1) {
                throw new IllegalArgumentException("Cannot sign messages using a wallet with multiple keystores - a single key is required");
            }
            if(!wallet.getKeystores().get(0).hasPrivateKey() && wallet.getKeystores().get(0).getSource() != KeystoreSource.HW_USB) {
                throw new IllegalArgumentException("Cannot sign messages using a wallet without a seed or USB keystore");
            }
        }

        this.wallet = wallet;
        this.walletNode = walletNode;

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeaderText(title == null ? (wallet == null ? "Verify Message" : "Sign/Verify Message") : title);

        Image image = new Image("image/seed.png", 50, 50, false, false);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setSmooth(false);
            imageView.setImage(image);
            dialogPane.setGraphic(imageView);
        }

        VBox vBox = new VBox();
        vBox.setSpacing(20);

        Form form = new Form();
        Fieldset fieldset = new Fieldset();
        fieldset.setText("");
        fieldset.setSpacing(10);

        Field addressField = new Field();
        addressField.setText("Address:");
        address = new TextField();
        address.getStyleClass().add("id");
        address.setEditable(walletNode == null);
        addressField.getInputs().add(address);

        if(walletNode != null) {
            address.setText(wallet.getAddress(walletNode).toString());
        }

        Field messageField = new Field();
        messageField.setText("Message:");
        message = new TextArea();
        message.setWrapText(true);
        message.setPrefRowCount(10);
        message.setStyle("-fx-pref-height: 180px");
        messageField.getInputs().add(message);

        Field signatureField = new Field();
        signatureField.setText("Signature:");
        signature = new TextArea();
        signature.getStyleClass().add("id");
        signature.setPrefRowCount(2);
        signature.setStyle("-fx-pref-height: 60px");
        signature.setWrapText(true);
        signatureField.getInputs().add(signature);

        Field formatField = new Field();
        formatField.setText("Format:");
        formatGroup = new ToggleGroup();
        formatElectrum = new ToggleButton("Standard (Electrum)");
        formatTrezor = new ToggleButton("BIP137 (Trezor)");
        SegmentedButton formatButtons = new SegmentedButton(formatElectrum, formatTrezor);
        formatButtons.setToggleGroup(formatGroup);
        formatField.getInputs().add(formatButtons);

        formatGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            electrumSignatureFormat = (newValue == formatElectrum);
        });

        formatButtons.setDisable(wallet != null && walletNode != null && wallet.getScriptType() == ScriptType.P2PKH);

        fieldset.getChildren().addAll(addressField, messageField, signatureField, formatField);
        form.getChildren().add(fieldset);
        dialogPane.setContent(form);

        if(msg != null) {
            message.setText(msg);
            address.setEditable(false);
            message.setEditable(false);
            signature.setEditable(false);
            formatButtons.setDisable(true);
        }

        ButtonType signButtonType = new javafx.scene.control.ButtonType("Sign", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType verifyButtonType = new javafx.scene.control.ButtonType("Verify", ButtonBar.ButtonData.NEXT_FORWARD);
        ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.CANCEL_CLOSE);

        if(buttons.length > 0) {
            dialogPane.getButtonTypes().addAll(buttons);

            ButtonType customSignButtonType = Arrays.stream(buttons).filter(buttonType -> buttonType.getText().toLowerCase().contains("sign")).findFirst().orElse(null);
            if(customSignButtonType != null) {
                Button customSignButton = (Button)dialogPane.lookupButton(customSignButtonType);
                customSignButton.setDefaultButton(true);
                customSignButton.setOnAction(event -> {
                    customSignButton.setDisable(true);
                    signMessage();
                    setResult(ButtonBar.ButtonData.OK_DONE);
                });
            }
        } else {
            dialogPane.getButtonTypes().addAll(signButtonType, verifyButtonType, doneButtonType);

            Button signButton = (Button) dialogPane.lookupButton(signButtonType);
            signButton.setDisable(wallet == null);
            signButton.setOnAction(event -> {
                signMessage();
            });

            Button verifyButton = (Button) dialogPane.lookupButton(verifyButtonType);
            verifyButton.setDefaultButton(false);
            verifyButton.setOnAction(event -> {
                verifyMessage();
            });

            boolean validAddress = isValidAddress();
            signButton.setDisable(!validAddress || (wallet == null));
            verifyButton.setDisable(!validAddress);

            ValidationSupport validationSupport = new ValidationSupport();
            Platform.runLater(() -> {
                validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
                validationSupport.registerValidator(address, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Invalid address", !isValidAddress()));
            });

            address.textProperty().addListener((observable, oldValue, newValue) -> {
                boolean valid = isValidAddress();
                signButton.setDisable(!valid || (wallet == null));
                verifyButton.setDisable(!valid);

                if(valid && wallet != null) {
                    try {
                        Address address = getAddress();
                        setWalletNodeFromAddress(wallet, address);
                    } catch(InvalidAddressException e) {
                        //can't happen
                    }
                }
            });
        }

        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            if(ButtonBar.ButtonData.APPLY.equals(getResult())) {
                event.consume();
                return;
            }

            if(!closed) {
                EventManager.get().unregister(this);
                closed = true;
            }
        });

        AppServices.onEscapePressed(dialogPane.getScene(), () -> setResult(ButtonBar.ButtonData.CANCEL_CLOSE));
        AppServices.moveToActiveWindowScreen(this);
        setResultConverter(dialogButton -> dialogButton == signButtonType || dialogButton == verifyButtonType ? ButtonBar.ButtonData.APPLY : dialogButton.getButtonData());

        Platform.runLater(() -> {
            if(address.getText().isEmpty()) {
                address.requestFocus();
            } else if(message.getText().isEmpty()) {
                message.requestFocus();
            }

            formatGroup.selectToggle(formatElectrum);
        });
    }

    private Address getAddress()throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    public String getSignature() {
        return signature.getText();
    }

    /**
     * Use the Electrum signing format, which uses the non-segwit compressed signing parameters for both segwit types (p2sh-p2wpkh and p2wpkh)
     *
     * @param electrumSignatureFormat
     */
    public void setElectrumSignatureFormat(boolean electrumSignatureFormat) {
        formatGroup.selectToggle(electrumSignatureFormat ? formatElectrum : formatTrezor);
        this.electrumSignatureFormat = electrumSignatureFormat;
    }

    private boolean isValidAddress() {
        try {
            Address address = getAddress();
            return address.getScriptType() != ScriptType.P2TR;
        } catch (InvalidAddressException e) {
            return false;
        }
    }

    private void setWalletNodeFromAddress(Wallet wallet, Address address) {
        walletNode = wallet.getWalletAddresses().get(address);
    }

    private void signMessage() {
        if(walletNode == null) {
            AppServices.showErrorDialog("Address not in wallet", "The provided address is not present in the currently selected wallet.");
            return;
        }

        if(wallet.containsPrivateKeys()) {
            if(wallet.isEncrypted()) {
                EventManager.get().post(new RequestOpenWalletsEvent());
            } else {
                signUnencryptedKeystore(wallet);
            }
        } else if(wallet.containsSource(KeystoreSource.HW_USB)) {
            signUsbKeystore(wallet);
        }
    }

    private void signUnencryptedKeystore(Wallet decryptedWallet) {
        try {
            //Note we can expect a single keystore due to the check above
            Keystore keystore = decryptedWallet.getKeystores().get(0);
            ECKey privKey = keystore.getKey(walletNode);
            ScriptType scriptType = electrumSignatureFormat ? ScriptType.P2PKH : decryptedWallet.getScriptType();
            String signatureText = privKey.signMessage(message.getText().trim(), scriptType);
            signature.clear();
            signature.appendText(signatureText);
            privKey.clear();
        } catch(Exception e) {
            log.error("Could not sign message", e);
            AppServices.showErrorDialog("Could not sign message", e.getMessage());
        }
    }

    private void signUsbKeystore(Wallet usbWallet) {
        List<String> fingerprints = List.of(usbWallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        KeyDerivation fullDerivation = usbWallet.getKeystores().get(0).getKeyDerivation().extend(walletNode.getDerivation());
        DeviceSignMessageDialog deviceSignMessageDialog = new DeviceSignMessageDialog(fingerprints, usbWallet, message.getText().trim(), fullDerivation);
        Optional<String> optSignature = deviceSignMessageDialog.showAndWait();
        if(optSignature.isPresent()) {
            signature.clear();
            signature.appendText(optSignature.get());
        }
    }

    private void verifyMessage() {
        try {
            //Find ECKey from message and signature
            //http://www.secg.org/download/aid-780/sec1-v2.pdf section 4.1.6
            boolean verified = false;
            try {
                ECKey signedMessageKey = ECKey.signedMessageToKey(message.getText().trim(), signature.getText().trim(), false);
                verified = verifyMessage(signedMessageKey);
            } catch(SignatureException e) {
                //ignore
            }

            if(!verified) {
                try {
                    ECKey electrumSignedMessageKey = ECKey.signedMessageToKey(message.getText(), signature.getText(), true);
                    verified = verifyMessage(electrumSignedMessageKey);
                } catch(SignatureException e) {
                    //ignore
                }
            }

            if(verified) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                AppServices.setStageIcon(alert.getDialogPane().getScene().getWindow());
                alert.setTitle("Verification Succeeded");
                alert.setHeaderText("Verification Succeeded");
                alert.setContentText("The signature verified against the message.");
                AppServices.moveToActiveWindowScreen(alert);
                alert.showAndWait();
            } else {
                AppServices.showErrorDialog("Verification failed", "The provided signature did not match the message for this address.");
            }
        } catch(IllegalArgumentException e) {
            AppServices.showErrorDialog("Could not verify message", e.getMessage());
        } catch(Exception e) {
            log.error("Could not verify message", e);
            AppServices.showErrorDialog("Could not verify message", e.getMessage());
        }
    }

    private boolean verifyMessage(ECKey signedMessageKey) throws InvalidAddressException, SignatureException {
        Address providedAddress = getAddress();
        ScriptType scriptType = providedAddress.getScriptType();
        if(scriptType == ScriptType.P2SH) {
            scriptType = ScriptType.P2SH_P2WPKH;
        }
        if(!ScriptType.getScriptTypesForPolicyType(PolicyType.SINGLE).contains(scriptType)) {
            throw new IllegalArgumentException("Only single signature P2PKH, P2SH-P2WPKH or P2WPKH addresses can verify messages.");
        }

        Address signedMessageAddress = scriptType.getAddress(signedMessageKey);
        return providedAddress.equals(signedMessageAddress);
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        Storage storage = event.getStorage(wallet);
        if(storage == null) {
            //Another window, ignore
            return;
        }

        WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(wallet.copy(), password.get());
            decryptWalletService.setOnSucceeded(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                Wallet decryptedWallet = decryptWalletService.getValue();
                signUnencryptedKeystore(decryptedWallet);
                decryptedWallet.clearPrivate();
            });
            decryptWalletService.setOnFailed(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
            });
            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
            decryptWalletService.start();
        }
    }
}
