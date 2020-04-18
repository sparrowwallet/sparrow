package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.ExtendedPublicKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Keystore;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class KeystoreController extends WalletFormController implements Initializable {
    private Keystore keystore;

    @FXML
    private TextField label;

    @FXML
    private TextArea xpub;

    @FXML
    private TextField derivation;

    @FXML
    private TextField fingerprint;

    private ValidationSupport validationSupport;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setKeystore(WalletForm walletForm, Keystore keystore) {
        this.keystore = keystore;
        setWalletForm(walletForm);
    }

    @Override
    public void initializeView() {
        Platform.runLater(this::setupValidation);

        label.setText(keystore.getLabel());

        if(keystore.getExtendedPublicKey() != null) {
            xpub.setText(keystore.getExtendedPublicKey().toString());
        }

        if(keystore.getKeyDerivation() != null) {
            derivation.setText(keystore.getKeyDerivation().getDerivationPath());
            fingerprint.setText(keystore.getKeyDerivation().getMasterFingerprint());
        }

        label.textProperty().addListener((observable, oldValue, newValue) -> keystore.setLabel(newValue));
        fingerprint.textProperty().addListener((observable, oldValue, newValue) -> keystore.setKeyDerivation(new KeyDerivation(newValue, keystore.getKeyDerivation().getDerivationPath())));
        derivation.textProperty().addListener((observable, oldValue, newValue) -> keystore.setKeyDerivation(new KeyDerivation(keystore.getKeyDerivation().getMasterFingerprint(), newValue)));
        xpub.textProperty().addListener((observable, oldValue, newValue) -> keystore.setExtendedPublicKey(ExtendedPublicKey.fromDescriptor(newValue)));
    }

    public TextField getLabel() {
        return label;
    }

    private void setupValidation() {
        validationSupport = new ValidationSupport();

        validationSupport.registerValidator(label, Validator.combine(
                Validator.createEmptyValidator("Label is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is not unique", walletForm.getWallet().getKeystores().stream().filter(k -> k != keystore).map(Keystore::getLabel).collect(Collectors.toList()).contains(newValue)),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Label is too long", newValue.length() > 16)
        ));

        validationSupport.registerValidator(xpub, Validator.combine(
                Validator.createEmptyValidator("xPub is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "xPub is invalid", !ExtendedPublicKey.isValid(newValue))
        ));

        validationSupport.registerValidator(derivation, Validator.combine(
                Validator.createEmptyValidator("Derivation is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Derivation is invalid", !KeyDerivation.isValid(newValue))
        ));

        validationSupport.registerValidator(fingerprint, Validator.combine(
                Validator.createEmptyValidator("Master fingerprint is required"),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Master fingerprint is invalid", (newValue.length() != 8 || !Utils.isHex(newValue)))
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }
}
