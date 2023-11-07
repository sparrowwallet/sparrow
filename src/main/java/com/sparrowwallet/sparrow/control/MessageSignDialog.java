package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.crypto.Bip322;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.glyphfont.Glyph;
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
import java.util.Locale;
import java.util.Optional;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class MessageSignDialog extends Dialog<ButtonBar.ButtonData> {
    private static final Logger log = LoggerFactory.getLogger(MessageSignDialog.class);

    private final TextField address;
    private final TextArea message;
    private final TextArea signature;
    private final ToggleGroup formatGroup;
    private final ToggleButton formatTrezor;
    private final ToggleButton formatElectrum;
    private final ToggleButton formatBip322;
    private final Wallet wallet;
    private WalletNode walletNode;
    private boolean canSign;
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
        if(walletNode != null) {
            checkWalletSigning(walletNode.getWallet());
            this.canSign = canSign(walletNode.getWallet());
        }

        if(wallet != null) {
            checkWalletSigning(wallet);
            this.canSign = canSign(wallet);
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
        address.setTooltip(new Tooltip("Only singlesig addresses can sign"));
        addressField.getInputs().add(address);

        if(walletNode != null) {
            address.setText(walletNode.getAddress().toString());
        }

        Field messageField = new Field();
        messageField.setText("Message:");
        message = new TextArea();
        message.setWrapText(true);
        message.setPrefRowCount(8);
        message.setStyle("-fx-pref-height: 160px");
        messageField.getInputs().add(message);

        Field signatureField = new Field();
        signatureField.setText("Signature:");
        signature = new TextArea();
        signature.getStyleClass().add("id");
        signature.setPrefRowCount(4);
        signature.setStyle("-fx-pref-height: 80px");
        signature.setWrapText(true);
        signature.setOnMouseClicked(event -> signature.selectAll());
        signatureField.getInputs().add(signature);

        Field formatField = new Field();
        formatField.setText("Format:");
        formatGroup = new ToggleGroup();
        formatElectrum = new ToggleButton("Standard (Electrum)");
        formatTrezor = new ToggleButton("BIP137 (Trezor)");
        formatBip322 = new ToggleButton("BIP322 (Simple)");
        SegmentedButton formatButtons = new SegmentedButton(formatElectrum, formatTrezor, formatBip322);
        formatButtons.setToggleGroup(formatGroup);
        formatField.getInputs().add(formatButtons);

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

        ButtonType showQrButtonType = new javafx.scene.control.ButtonType("Sign by QR", ButtonBar.ButtonData.LEFT);
        ButtonType signButtonType = new javafx.scene.control.ButtonType("Sign", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType verifyButtonType = new javafx.scene.control.ButtonType("Verify", ButtonBar.ButtonData.NEXT_FORWARD);
        ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.CANCEL_CLOSE);

        if(buttons.length > 0) {
            dialogPane.getButtonTypes().addAll(buttons);

            ButtonType customSignButtonType = Arrays.stream(buttons).filter(buttonType -> buttonType.getText().toLowerCase(Locale.ROOT).contains("sign")).findFirst().orElse(null);
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
            dialogPane.getButtonTypes().addAll(showQrButtonType, signButtonType, verifyButtonType, doneButtonType);

            Button showQrButton = (Button) dialogPane.lookupButton(showQrButtonType);
            showQrButton.setDisable(wallet == null);
            showQrButton.setGraphic(getGlyph(new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.QRCODE)));
            showQrButton.setGraphicTextGap(5);
            showQrButton.setOnAction(event -> {
                showQr();
            });

            Button signButton = (Button) dialogPane.lookupButton(signButtonType);
            signButton.setDisable(!canSign);
            signButton.setGraphic(getGlyph(getSignGlyph()));
            signButton.setGraphicTextGap(5);
            signButton.setOnAction(event -> {
                signMessage();
            });

            Button verifyButton = (Button) dialogPane.lookupButton(verifyButtonType);
            verifyButton.setDefaultButton(false);
            verifyButton.setOnAction(event -> {
                verifyMessage();
            });

            boolean validAddress = isValidAddress();
            showQrButton.setDisable(!validAddress || (wallet == null));
            signButton.setDisable(!validAddress || !canSign);
            verifyButton.setDisable(!validAddress);

            ValidationSupport validationSupport = new ValidationSupport();
            Platform.runLater(() -> {
                validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
                validationSupport.registerValidator(address, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Invalid address", !isValidAddress()));
            });

            address.textProperty().addListener((observable, oldValue, newValue) -> {
                boolean valid = isValidAddress();
                showQrButton.setDisable(!valid || (wallet == null));
                signButton.setDisable(!valid || !canSign);
                verifyButton.setDisable(!valid);

                if(valid) {
                    try {
                        Address address = getAddress();
                        setFormatFromScriptType(address.getScriptType());
                        if(wallet != null) {
                            setWalletNodeFromAddress(wallet, address);
                        }
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
        setResultConverter(dialogButton -> dialogButton == showQrButtonType || dialogButton == signButtonType || dialogButton == verifyButtonType ? ButtonBar.ButtonData.APPLY : dialogButton.getButtonData());

        Platform.runLater(() -> {
            if(address.getText().isEmpty()) {
                address.requestFocus();
            } else if(message.getText().isEmpty()) {
                message.requestFocus();
            }

            if(wallet != null && walletNode != null) {
                setFormatFromScriptType(wallet.getScriptType());
            } else {
                formatGroup.selectToggle(formatElectrum);
            }
        });
    }

    private void checkWalletSigning(Wallet wallet) {
        if(wallet.getKeystores().size() != 1) {
            throw new IllegalArgumentException("Cannot sign messages using a wallet with multiple keystores - a single key is required");
        }
    }

    private boolean canSign(Wallet wallet) {
        return wallet.getKeystores().get(0).hasPrivateKey()
                || wallet.getKeystores().get(0).getSource() == KeystoreSource.HW_USB
                || wallet.getKeystores().get(0).getWalletModel().isCard();
    }

    private Address getAddress()throws InvalidAddressException {
        return Address.fromString(address.getText());
    }

    public String getSignature() {
        return signature.getText();
    }

    private boolean isValidAddress() {
        try {
            Address address = getAddress();
            return address.getScriptType().isAllowed(PolicyType.SINGLE) || address.getScriptType() == ScriptType.P2SH;
        } catch (InvalidAddressException e) {
            return false;
        }
    }

    private void setWalletNodeFromAddress(Wallet wallet, Address address) {
        walletNode = wallet.getWalletAddresses().get(address);
    }

    private void setFormatFromScriptType(ScriptType scriptType) {
        formatElectrum.setDisable(scriptType == ScriptType.P2TR);
        formatTrezor.setDisable(scriptType == ScriptType.P2TR || scriptType == ScriptType.P2PKH);
        formatBip322.setDisable(scriptType != ScriptType.P2WPKH && scriptType != ScriptType.P2TR);
        if(scriptType == ScriptType.P2TR) {
            formatGroup.selectToggle(formatBip322);
        } else if(formatGroup.getSelectedToggle() == null || scriptType == ScriptType.P2PKH || (scriptType != ScriptType.P2WPKH && formatBip322.isSelected())) {
            formatGroup.selectToggle(formatElectrum);
        }
    }

    private boolean isBip322() {
        return formatBip322.isSelected();
    }

    private boolean isElectrumSignatureFormat() {
        return formatElectrum.isSelected();
    }

    private void signMessage() {
        if(walletNode == null) {
            AppServices.showErrorDialog("Address not in wallet", "The provided address is not present in the currently selected wallet.");
            return;
        }

        if(!canSign) {
            AppServices.showErrorDialog("Wallet can't sign", "This wallet cannot sign a message.");
            return;
        }

        //Note we can expect a single keystore due to the check in the constructor
        Wallet signingWallet = walletNode.getWallet();
        if(signingWallet.getKeystores().get(0).hasPrivateKey()) {
            if(signingWallet.isEncrypted()) {
                EventManager.get().post(new RequestOpenWalletsEvent());
            } else {
                signUnencryptedKeystore(signingWallet);
            }
        } else if(signingWallet.containsSource(KeystoreSource.HW_USB) || wallet.getKeystores().get(0).getWalletModel().isCard()) {
            signDeviceKeystore(signingWallet);
        }
    }

    private void signUnencryptedKeystore(Wallet decryptedWallet) {
        try {
            Keystore keystore = decryptedWallet.getKeystores().get(0);
            ECKey privKey = keystore.getKey(walletNode);
            String signatureText;
            if(isBip322()) {
                ScriptType scriptType = decryptedWallet.getScriptType();
                signatureText = Bip322.signMessageBip322(scriptType, message.getText().trim(), privKey);
            } else {
                ScriptType scriptType = isElectrumSignatureFormat() ? ScriptType.P2PKH : decryptedWallet.getScriptType();
                signatureText = privKey.signMessage(message.getText().trim(), scriptType);
            }
            signature.clear();
            signature.appendText(signatureText);
            privKey.clear();
        } catch(Exception e) {
            log.error("Could not sign message", e);
            AppServices.showErrorDialog("Could not sign message", e.getMessage());
        }
    }

    private void signDeviceKeystore(Wallet deviceWallet) {
        List<String> fingerprints = List.of(deviceWallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        KeyDerivation fullDerivation = deviceWallet.getKeystores().get(0).getKeyDerivation().extend(walletNode.getDerivation());
        DeviceSignMessageDialog deviceSignMessageDialog = new DeviceSignMessageDialog(fingerprints, deviceWallet, message.getText().trim(), fullDerivation);
        deviceSignMessageDialog.initOwner(getDialogPane().getScene().getWindow());
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
                ECKey signedMessageKey = ECKey.signedMessageToKey(message.getText().trim(), signature.getText().trim(), true);
                verified = verifyMessage(signedMessageKey);
                if(verified) {
                    formatGroup.selectToggle(formatElectrum);
                }
            } catch(SignatureException e) {
                //ignore
            }

            if(!verified) {
                try {
                    ECKey electrumSignedMessageKey = ECKey.signedMessageToKey(message.getText(), signature.getText(), false);
                    verified = verifyMessage(electrumSignedMessageKey);
                    if(verified) {
                        formatGroup.selectToggle(formatTrezor);
                    }
                } catch(SignatureException e) {
                    //ignore
                }
            }

            if(!verified && Bip322.isSupported(getAddress().getScriptType()) && !signature.getText().trim().isEmpty()) {
                try {
                    verified = Bip322.verifyMessageBip322(getAddress().getScriptType(), getAddress(), message.getText().trim(), signature.getText().trim());
                    if(verified) {
                        formatGroup.selectToggle(formatBip322);
                    }
                } catch(SignatureException e) {
                    //ignore
                }
            }

            if(verified) {
                AppServices.showSuccessDialog("Verification Succeeded", "The signature verified against the message.");
            } else {
                AppServices.showErrorDialog("Verification Failed", "The provided signature did not match the message for this address.");
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

    private void showQr() {
        if(walletNode == null) {
            AppServices.showErrorDialog("Address not in wallet", "The provided address is not present in the currently selected wallet.");
            return;
        }

        //Note we can expect a single keystore due to the check in the constructor
        KeyDerivation firstDerivation = walletNode.getWallet().getKeystores().get(0).getKeyDerivation();
        String derivationPath = KeyDerivation.writePath(firstDerivation.extend(walletNode.getDerivation()).getDerivation(), false);

        String qrText = "signmessage " + derivationPath + " ascii:" + message.getText().trim();
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(qrText, true);
        qrDisplayDialog.initOwner(getDialogPane().getScene().getWindow());
        Optional<ButtonType> optButtonType = qrDisplayDialog.showAndWait();
        if(optButtonType.isPresent() && optButtonType.get().getButtonData() == ButtonBar.ButtonData.NEXT_FORWARD) {
            scanQr();
        }
    }

    private void scanQr() {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(getDialogPane().getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.payload != null) {
                signature.clear();
                signature.appendText(result.payload);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a signature.");
            }
        }
    }

    protected Glyph getSignGlyph() {
        if(wallet != null) {
            if(wallet.containsSource(KeystoreSource.HW_USB)) {
                return new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
            } else if(wallet.getKeystores().get(0).getWalletModel().isCard()) {
                return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WIFI);
            }
        }

        return new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PEN_FANCY);
    }

    private static Glyph getGlyph(Glyph glyph) {
        glyph.setFontSize(11);
        return glyph;
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        Storage storage = event.getStorage(wallet);
        if(storage == null) {
            //Another window, ignore
            return;
        }

        WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
        dlg.initOwner(getDialogPane().getScene().getWindow());
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(walletNode.getWallet().copy(), password.get());
            decryptWalletService.setOnSucceeded(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                Wallet decryptedWallet = decryptWalletService.getValue();
                signUnencryptedKeystore(decryptedWallet);
                decryptedWallet.clearPrivate();
            });
            decryptWalletService.setOnFailed(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                AppServices.showErrorDialog("Incorrect Password", "The password was incorrect.");
            });
            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
            decryptWalletService.start();
        }
    }
}
