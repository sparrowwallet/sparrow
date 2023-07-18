package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class WalletNameDialog extends Dialog<WalletNameDialog.NameAndBirthDate> {
    private final CustomTextField name;
    private final CheckBox existingCheck;
    private final DatePicker existingPicker;

    public WalletNameDialog() {
        this("");
    }

    public WalletNameDialog(String initialName) {
        this(initialName, false);
    }

    public WalletNameDialog(String initialName, boolean hasExistingTransactions) {
        this(initialName, hasExistingTransactions, null);
    }

    public WalletNameDialog(String initialName, boolean hasExistingTransactions, Date startDate) {
        this(initialName, hasExistingTransactions, startDate, false);
    }

    public WalletNameDialog(String initialName, boolean hasExistingTransactions, Date startDate, boolean rename) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        boolean requestBirthDate = !rename && (Config.get().getServerType() == null || Config.get().getServerType() == ServerType.BITCOIN_CORE);

        setTitle("Wallet Name");
        dialogPane.setHeaderText("Enter a name for this wallet:");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(460);
        dialogPane.setPrefHeight(requestBirthDate ? 250 : 200);
        AppServices.moveToActiveWindowScreen(this);

        Glyph wallet = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WALLET);
        wallet.setFontSize(50);
        dialogPane.setGraphic(wallet);

        final VBox content = new VBox(20);
        name = (CustomTextField)TextFields.createClearableTextField();
        name.setText(initialName);
        name.setTextFormatter(new TextFormatter<>((change) -> {
            change.setText(change.getText().replaceAll("[\\\\/:*?\"<>|]", "_"));
            return change;
        }));
        content.getChildren().add(name);

        HBox existingBox = new HBox(10);
        existingCheck = new CheckBox("Has existing transactions");
        existingCheck.setPadding(new Insets(5, 0, 0, 0));
        existingBox.getChildren().add(existingCheck);

        existingPicker = new DatePicker();
        existingPicker.setConverter(new DateStringConverter());
        existingPicker.setEditable(false);
        existingPicker.setPrefWidth(130);
        existingPicker.managedProperty().bind(existingPicker.visibleProperty());
        existingPicker.setVisible(false);
        existingBox.getChildren().add(existingPicker);

        HelpLabel helpLabel = new HelpLabel();
        helpLabel.setHelpText("Select an approximate date earlier than the first wallet transaction.");
        helpLabel.setTranslateY(5);
        helpLabel.managedProperty().bind(helpLabel.visibleProperty());
        helpLabel.visibleProperty().bind(existingPicker.visibleProperty());
        existingBox.getChildren().add(helpLabel);

        existingCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue) {
                existingCheck.setText("Has existing transactions starting from");
                existingPicker.setVisible(true);
            } else {
                existingCheck.setText("Has existing transactions");
                existingPicker.setVisible(false);
            }
        });

        if(requestBirthDate) {
            content.getChildren().add(existingBox);
            if(hasExistingTransactions) {
                existingCheck.setSelected(true);
            }
            if(startDate != null) {
                existingPicker.setValue(startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            }
        }

        dialogPane.setContent(content);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(name, Validator.combine(
                    Validator.createEmptyValidator("Wallet name is required"),
                    (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Wallet name is not unique", Storage.walletExists(newValue))
            ));
            validationSupport.registerValidator(existingPicker, Validator.combine(
                    (Control c, LocalDate newValue) -> ValidationResult.fromErrorIf( c, "Birth date not specified", existingCheck.isSelected() && newValue == null)
            ));
        });

        final ButtonType okButtonType = new javafx.scene.control.ButtonType((rename ? "Rename" : "Create") + " Wallet", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() ->
                name.getText().trim().length() == 0 || Storage.walletExists(name.getText()) || (existingCheck.isSelected() && existingPicker.getValue() == null), name.textProperty(), existingCheck.selectedProperty(), existingPicker.valueProperty());
        okButton.disableProperty().bind(isInvalid);

        name.setPromptText("Wallet Name");
        Platform.runLater(name::requestFocus);
        setResultConverter(dialogButton -> dialogButton == okButtonType ? new NameAndBirthDate(name.getText().trim(), existingPicker.getValue()) : null);
    }

    public static class NameAndBirthDate {
        private final String name;
        private final Date birthDate;

        public NameAndBirthDate(String name, LocalDate birthLocalDate) {
            this.name = name;
            this.birthDate = (birthLocalDate == null ? null : Date.from(birthLocalDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
        }

        public String getName() {
            return name;
        }

        public Date getBirthDate() {
            return birthDate;
        }
    }
}
