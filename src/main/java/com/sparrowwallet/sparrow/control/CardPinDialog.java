package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

public class CardPinDialog extends Dialog<CardPinDialog.CardPinChange> {
    private final CustomPasswordField existingPin;
    private final CustomPasswordField newPin;
    private final CustomPasswordField newPinConfirm;
    private final CheckBox backupFirst;
    private final ButtonType okButtonType;

    public CardPinDialog() {
        this.existingPin = new ViewPasswordField();
        this.newPin = new ViewPasswordField();
        this.newPinConfirm = new ViewPasswordField();
        this.backupFirst = new CheckBox();

        final DialogPane dialogPane = getDialogPane();
        setTitle("Change Card PIN");
        dialogPane.setHeaderText("Enter the current PIN, and then the new PIN twice. PIN must be between 6 and 32 digits.");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(260);
        AppServices.moveToActiveWindowScreen(this);

        Glyph lock = new Glyph("FontAwesome", FontAwesome.Glyph.LOCK);
        lock.setFontSize(50);
        dialogPane.setGraphic(lock);

        Form form = new Form();
        Fieldset fieldset = new Fieldset();
        fieldset.setText("");
        fieldset.setSpacing(10);

        Field currentField = new Field();
        currentField.setText("Current PIN:");
        currentField.getInputs().add(existingPin);

        Field newField = new Field();
        newField.setText("New PIN:");
        newField.getInputs().add(newPin);

        Field confirmField = new Field();
        confirmField.setText("Confirm new PIN:");
        confirmField.getInputs().add(newPinConfirm);

        Field backupField = new Field();
        backupField.setText("Backup First:");
        backupField.getInputs().add(backupFirst);

        fieldset.getChildren().addAll(currentField, newField, confirmField, backupField);
        form.getChildren().add(fieldset);
        dialogPane.setContent(form);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(existingPin, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Incorrect PIN length", existingPin.getText().length() < 6 || existingPin.getText().length() > 32));
            validationSupport.registerValidator(newPin, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Incorrect PIN length", newPin.getText().length() < 6 || newPin.getText().length() > 32));
            validationSupport.registerValidator(newPinConfirm, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "PIN confirmation does not match", !newPinConfirm.getText().equals(newPin.getText())));
        });

        okButtonType = new javafx.scene.control.ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        okButton.setPrefWidth(130);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() -> existingPin.getText().length() < 6 || existingPin.getText().length() > 32
                        || newPin.getText().length() < 6 || newPin.getText().length() > 32
                        || !newPin.getText().equals(newPinConfirm.getText()),
                existingPin.textProperty(), newPin.textProperty(), newPinConfirm.textProperty());
        okButton.disableProperty().bind(isInvalid);

        Platform.runLater(existingPin::requestFocus);
        setResultConverter(dialogButton -> dialogButton == okButtonType ? new CardPinChange(existingPin.getText(), newPin.getText(), backupFirst.isSelected()) : null);
    }

    public record CardPinChange(String currentPin, String newPin, boolean backupFirst) { }
}
