package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
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
        this.passphrase = (CustomPasswordField) TextFields.createClearablePasswordField();

        final DialogPane dialogPane = getDialogPane();
        setTitle("Keystore Passphrase");
        dialogPane.setHeaderText("Please enter the passphrase for keystore: " + keystore.getLabel());
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(200);

        Glyph lock = new Glyph("FontAwesome5", FontAwesome5.Glyph.KEY);
        lock.setFontSize(50);
        dialogPane.setGraphic(lock);

        final VBox content = new VBox(10);
        content.setPrefHeight(50);
        content.getChildren().add(passphrase);

        dialogPane.setContent(content);
        passphrase.requestFocus();

        setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? passphrase.getText() : null);
    }
}
