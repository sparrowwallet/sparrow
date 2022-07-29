package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
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

import java.util.Locale;

public class WalletLabelDialog extends Dialog<String> {
    private static final int MAX_LABEL_LENGTH = 25;

    private final CustomTextField label;

    public WalletLabelDialog(String initialName) {
        this(initialName, "Account");
    }

    public WalletLabelDialog(String initialName, String walletType) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        setTitle(walletType + " Name");
        dialogPane.setHeaderText("Enter a name for this " + walletType.toLowerCase(Locale.ROOT) + ":");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(400);
        dialogPane.setPrefHeight(200);
        AppServices.moveToActiveWindowScreen(this);

        Glyph wallet = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WALLET);
        wallet.setFontSize(50);
        dialogPane.setGraphic(wallet);

        final VBox content = new VBox(20);
        label = (CustomTextField) TextFields.createClearableTextField();
        label.setText(initialName);
        label.setTextFormatter(new TextFormatter<>((change) -> {
            change.setText(change.getText().replaceAll("[\\\\/:*?\"<>|]", "_"));
            return change;
        }));
        content.getChildren().add(label);

        dialogPane.setContent(content);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater(() -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(label, Validator.combine(
                    Validator.createEmptyValidator(walletType + " name is required"),
                    (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Label too long", newValue != null && newValue.length() > MAX_LABEL_LENGTH)
            ));
        });

        final ButtonType okButtonType = new javafx.scene.control.ButtonType("Rename " + walletType, ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button)dialogPane.lookupButton(okButtonType);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() -> label.getText().length() == 0 || label.getText().length() > MAX_LABEL_LENGTH, label.textProperty());
        okButton.disableProperty().bind(isInvalid);

        label.setPromptText(walletType + " Name");
        Platform.runLater(label::requestFocus);
        setResultConverter(dialogButton -> dialogButton == okButtonType ? label.getText() : null);
    }
}
