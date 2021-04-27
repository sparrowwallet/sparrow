package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
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
        this.passphrase = (CustomPasswordField) TextFields.createClearablePasswordField();

        final DialogPane dialogPane = getDialogPane();
        setTitle("Keystore Passphrase" + (walletName != null ? " - " + walletName : ""));
        dialogPane.setHeaderText("Please enter the passphrase for keystore: \n" + keystore.getLabel());
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

        dialogPane.setContent(content);
        Platform.runLater(passphrase::requestFocus);

        setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? passphrase.getText() : null);
    }
}
