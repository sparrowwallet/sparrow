package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.glyphfont.Glyph;

public class KeystorePassphraseDialog extends Dialog<String> {
    private final CustomPasswordField passphrase;
    private final ObjectProperty<byte[]> masterFingerprint = new SimpleObjectProperty<>();

    public KeystorePassphraseDialog(Keystore keystore) {
        this(null, keystore);
    }

    public KeystorePassphraseDialog(String walletName, Keystore keystore) {
        this(walletName, keystore, false);
    }

    public KeystorePassphraseDialog(String walletName, Keystore keystore, boolean confirm) {
        this.passphrase = new ViewPasswordField();

        final DialogPane dialogPane = getDialogPane();
        setTitle("Keystore Passphrase" + (walletName != null ? " for " + walletName : ""));
        dialogPane.setHeaderText((confirm ? "Re-enter" : "Enter") + " the BIP39 passphrase\n" + (confirm ? "to confirm:" : "for keystore: " + keystore.getLabel()));
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(200);
        AppServices.moveToActiveWindowScreen(this);

        Glyph key = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.KEY);
        key.setFontSize(50);
        dialogPane.setGraphic(key);

        final VBox content = new VBox(10);
        content.setPrefHeight(50);
        content.getChildren().add(passphrase);

        passphrase.textProperty().addListener((observable, oldValue, passphrase) -> {
            masterFingerprint.set(getMasterFingerprint(keystore, passphrase));
        });

        HBox fingerprintBox = new HBox(10);
        fingerprintBox.setAlignment(Pos.CENTER_LEFT);
        Label fingerprintLabel = new Label("Master fingerprint:");
        TextField fingerprintHex = new TextField();
        fingerprintHex.setDisable(true);
        fingerprintHex.setMaxWidth(80);
        fingerprintHex.getStyleClass().addAll("fixed-width");
        fingerprintHex.setStyle("-fx-opacity: 0.6");
        masterFingerprint.addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                fingerprintHex.setText(Utils.bytesToHex(newValue));
            }
        });
        LifeHashIcon lifeHashIcon = new LifeHashIcon();
        lifeHashIcon.dataProperty().bind(masterFingerprint);
        HelpLabel helpLabel = new HelpLabel();
        helpLabel.setHelpText("All passphrases create valid wallets." +
                "\nThe master fingerprint identifies the keystore and changes as the passphrase changes." +
                "\n" + (confirm ? "Take a moment to identify it before proceeding." : "Make sure you recognise it before proceeding."));
        fingerprintBox.getChildren().addAll(fingerprintLabel, fingerprintHex, lifeHashIcon, helpLabel);
        content.getChildren().add(fingerprintBox);

        masterFingerprint.set(getMasterFingerprint(keystore, ""));

        Glyph warnGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        warnGlyph.getStyleClass().add("warn-icon");
        warnGlyph.setFontSize(12);
        Label warnLabel = new Label((confirm ? "Note" : "Check") + " the master fingerprint before proceeding!", warnGlyph);
        warnLabel.setGraphicTextGap(5);
        content.getChildren().add(warnLabel);

        dialogPane.setContent(content);
        Platform.runLater(passphrase::requestFocus);

        setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? passphrase.getText() : null);
    }

    private byte[] getMasterFingerprint(Keystore keystore, String passphrase) {
        try {
            Keystore copyKeystore = keystore.copy();
            copyKeystore.getSeed().setPassphrase(passphrase);
            return copyKeystore.getExtendedMasterPrivateKey().getKey().getFingerprint();
        } catch(MnemonicException e) {
            throw new RuntimeException(e);
        }
    }
}
