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
import com.sparrowwallet.sparrow.AppController;
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
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

import java.security.SignatureException;
import java.util.List;
import java.util.Optional;

import static com.sparrowwallet.sparrow.AppController.setStageIcon;

public class MessageSignDialog extends Dialog<ButtonBar.ButtonData> {
    private static final Logger log = LoggerFactory.getLogger(MessageSignDialog.class);

    private final TextField address;
    private final TextArea message;
    private final TextArea signature;
    private final Wallet wallet;
    private WalletNode walletNode;

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
        if(wallet != null) {
            if(wallet.getKeystores().size() != 1) {
                throw new IllegalArgumentException("Cannot sign messages using a wallet with multiple keystores - a single key is required");
            }
            if(!wallet.getKeystores().get(0).hasSeed() && wallet.getKeystores().get(0).getSource() != KeystoreSource.HW_USB) {
                throw new IllegalArgumentException("Cannot sign messages using a wallet without a seed or USB keystore");
            }
        }

        this.wallet = wallet;
        this.walletNode = walletNode;

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        AppController.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeaderText(wallet == null ? "Verify Message" : "Sign/Verify Message");

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

        fieldset.getChildren().addAll(addressField, messageField, signatureField);
        form.getChildren().add(fieldset);
        dialogPane.setContent(form);

        ButtonType signButtonType = new javafx.scene.control.ButtonType("Sign", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType verifyButtonType = new javafx.scene.control.ButtonType("Verify", ButtonBar.ButtonData.NEXT_FORWARD);
        ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(signButtonType, verifyButtonType, doneButtonType);

        Button signButton = (Button)dialogPane.lookupButton(signButtonType);
        signButton.setDisable(wallet == null);
        signButton.setOnAction(event -> {
            signMessage();
        });

        Button verifyButton = (Button)dialogPane.lookupButton(verifyButtonType);
        verifyButton.setDefaultButton(false);
        verifyButton.setOnAction(event -> {
            verifyMessage();
        });

        boolean validAddress = isValidAddress();
        signButton.setDisable(!validAddress || (wallet == null));
        verifyButton.setDisable(!validAddress);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater(() -> {
            validationSupport.registerValidator(address, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Invalid address", !isValidAddress()));
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
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

        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            if(ButtonBar.ButtonData.APPLY.equals(getResult())) {
                event.consume();
                return;
            }

            EventManager.get().unregister(this);
        });

        setResultConverter(dialogButton -> dialogButton == signButtonType || dialogButton == verifyButtonType ? ButtonBar.ButtonData.APPLY : ButtonBar.ButtonData.OK_DONE);
    }

    private Address getAddress()throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    private boolean isValidAddress() {
        try {
            getAddress();
            return true;
        } catch (InvalidAddressException e) {
            return false;
        }
    }

    private void setWalletNodeFromAddress(Wallet wallet, Address address) {
        walletNode = wallet.getWalletAddresses().get(address);
    }

    private void signMessage() {
        if(walletNode == null) {
            AppController.showErrorDialog("Address not in wallet", "The provided address is not present in the currently selected wallet.");
            return;
        }

        if(wallet.containsSeeds()) {
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
            String signatureText = privKey.signMessage(message.getText().trim(), decryptedWallet.getScriptType(), null);
            signature.clear();
            signature.appendText(signatureText);
        } catch(Exception e) {
            log.error("Could not sign message", e);
            AppController.showErrorDialog("Could not sign message", e.getMessage());
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
                setStageIcon(alert.getDialogPane().getScene().getWindow());
                alert.setTitle("Verification Succeeded");
                alert.setHeaderText("Verification Succeeded");
                alert.setContentText("The signature verified against the message.");
                alert.showAndWait();
            } else {
                AppController.showErrorDialog("Verification failed", "The provided signature did not match the message for this address.");
            }
        } catch(IllegalArgumentException e) {
            AppController.showErrorDialog("Could not verify message", e.getMessage());
        } catch(Exception e) {
            log.error("Could not verify message", e);
            AppController.showErrorDialog("Could not verify message", e.getMessage());
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
            throw new IllegalStateException("Wallet " + wallet + " without Storage");
        }

        WalletPasswordDialog dlg = new WalletPasswordDialog(WalletPasswordDialog.PasswordRequirement.LOAD);
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(wallet.copy(), password.get());
            decryptWalletService.setOnSucceeded(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.END, "Done"));
                Wallet decryptedWallet = decryptWalletService.getValue();
                signUnencryptedKeystore(decryptedWallet);
            });
            decryptWalletService.setOnFailed(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.END, "Failed"));
                AppController.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
            });
            EventManager.get().post(new StorageEvent(storage.getWalletFile(), TimedEvent.Action.START, "Decrypting wallet..."));
            decryptWalletService.start();
        }
    }
}
