package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class WalletBirthDateDialog extends Dialog<Date> {
    private final DatePicker birthDatePicker;

    public WalletBirthDateDialog(Date birthDate) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        setTitle("Wallet Birth Date");
        dialogPane.setHeaderText("Select an approximate date earlier than the first wallet transaction:");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(420);
        dialogPane.setPrefHeight(200);
        AppServices.moveToActiveWindowScreen(this);

        Glyph wallet = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.HISTORY);
        wallet.setFontSize(50);
        dialogPane.setGraphic(wallet);

        HBox datePickerBox = new HBox(10);
        Label label = new Label("Start scanning from:");
        label.setPadding(new Insets(5, 0, 0, 8));
        datePickerBox.getChildren().add(label);

        birthDatePicker = birthDate == null ? new DatePicker() : new DatePicker(birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        birthDatePicker.setEditable(false);
        birthDatePicker.setConverter(new DateStringConverter());

        datePickerBox.getChildren().add(birthDatePicker);

        dialogPane.setContent(datePickerBox);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(birthDatePicker, Validator.combine(
                    (Control c, LocalDate newValue) -> ValidationResult.fromErrorIf( c, "Birth date not specified", newValue == null)
            ));
        });

        final ButtonType okButtonType = new javafx.scene.control.ButtonType("Rescan Wallet", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() -> birthDatePicker.getValue() == null, birthDatePicker.valueProperty());
        okButton.disableProperty().bind(isInvalid);

        setResultConverter(dialogButton -> dialogButton == okButtonType ? Date.from(birthDatePicker.getValue().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()) : null);
    }
}
