package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;

public class KeystorePassphraseDialog extends Dialog<String> {
    private final CustomPasswordField passphrase;

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

        Glyph warnGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        warnGlyph.getStyleClass().add("warn-icon");
        warnGlyph.setFontSize(12);
        Label warnLabel = new Label("A BIP39 passphrase is not a wallet password!", warnGlyph);
        warnLabel.setGraphicTextGap(5);
        content.getChildren().add(warnLabel);

        dialogPane.setContent(content);
        Platform.runLater(passphrase::requestFocus);

        setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? passphrase.getText() : null);
    }
}
