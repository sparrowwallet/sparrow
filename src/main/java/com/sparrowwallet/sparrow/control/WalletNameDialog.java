package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

public class WalletNameDialog extends Dialog<String> {
    private final CustomTextField name;

    public WalletNameDialog() {
        this.name = (CustomTextField)TextFields.createClearableTextField();
        final DialogPane dialogPane = getDialogPane();

        setTitle("Wallet Password");
        dialogPane.setHeaderText("Enter a name for this wallet:");
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(200);

        Glyph wallet = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WALLET);
        wallet.setFontSize(50);
        dialogPane.setGraphic(wallet);

        final VBox content = new VBox(10);
        content.getChildren().add(name);

        dialogPane.setContent(content);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.registerValidator(name, Validator.combine(
                    Validator.createEmptyValidator("Wallet name is required"),
                    (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Wallet name is not unique", Storage.getStorage().getWalletFile(newValue).exists())
            ));
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        });

        final ButtonType okButtonType = new javafx.scene.control.ButtonType("New Wallet", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() ->
                name.getText().length() == 0 || Storage.getStorage().getWalletFile(name.getText()).exists(), name.textProperty());
        okButton.disableProperty().bind(isInvalid);

        name.setPromptText("Wallet Name");
        name.requestFocus();
        setResultConverter(dialogButton -> dialogButton == okButtonType ? name.getText() : null);
    }
}
