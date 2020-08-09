package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.sparrow.AppController;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;

public class WalletPasswordDialog extends Dialog<SecureString> {
    private final ButtonType okButtonType;
    private final PasswordRequirement requirement;
    private final CustomPasswordField password;
    private final CustomPasswordField passwordConfirm;
    private final CheckBox backupExisting;

    public WalletPasswordDialog(PasswordRequirement requirement) {
        this(null, requirement);
    }

    public WalletPasswordDialog(String walletName, PasswordRequirement requirement) {
        this.requirement = requirement;
        this.password = (CustomPasswordField)TextFields.createClearablePasswordField();
        this.passwordConfirm = (CustomPasswordField)TextFields.createClearablePasswordField();
        this.backupExisting = new CheckBox("Backup existing wallet first");

        final DialogPane dialogPane = getDialogPane();
        setTitle("Wallet Password" + (walletName != null ? " - " + walletName : ""));
        dialogPane.setHeaderText(walletName != null ? requirement.description.substring(0, requirement.description.length() - 1) + " for " + walletName + ":" : requirement.description);
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(260);

        Glyph lock = new Glyph("FontAwesome", FontAwesome.Glyph.LOCK);
        lock.setFontSize(50);
        dialogPane.setGraphic(lock);

        final VBox content = new VBox(10);
        content.setPrefHeight(100);
        content.getChildren().add(password);
        content.getChildren().add(passwordConfirm);

        if(requirement == PasswordRequirement.UPDATE_EMPTY || requirement == PasswordRequirement.UPDATE_SET) {
            content.getChildren().add(backupExisting);
            backupExisting.setSelected(true);
        }

        dialogPane.setContent(content);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.registerValidator(passwordConfirm, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Password confirmation does not match", !passwordConfirm.getText().equals(password.getText())));
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        });

        okButtonType = new javafx.scene.control.ButtonType(requirement.okButtonText, ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        okButton.setPrefWidth(130);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() -> passwordConfirm.isVisible() && !password.getText().equals(passwordConfirm.getText()), password.textProperty(), passwordConfirm.textProperty());
        okButton.disableProperty().bind(isInvalid);

        if(requirement != PasswordRequirement.UPDATE_NEW) {
            passwordConfirm.setVisible(false);
            passwordConfirm.setManaged(false);
        }

        if(requirement == PasswordRequirement.UPDATE_NEW || requirement == PasswordRequirement.UPDATE_EMPTY) {
            password.textProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue.isEmpty()) {
                    okButton.setText("No Password");
                    passwordConfirm.setVisible(false);
                    passwordConfirm.setManaged(false);
                } else {
                    okButton.setText("Set Password");
                    passwordConfirm.setVisible(true);
                    passwordConfirm.setManaged(true);
                }
            });
        }

        password.setPromptText("Password");
        Platform.runLater(password::requestFocus);
        passwordConfirm.setPromptText("Password Confirmation");

        setResultConverter(dialogButton -> dialogButton == okButtonType ? new SecureString(password.getText()) : null);
    }

    public boolean isBackupExisting() {
        return backupExisting.isSelected();
    }

    public enum PasswordRequirement {
        LOAD("Please enter the wallet password:", "Unlock"),
        UPDATE_NEW("Add a password to the wallet?\nLeave empty for none:", "No Password"),
        UPDATE_EMPTY("This wallet has no password.\nAdd a password to the wallet?\nLeave empty for none:", "No Password"),
        UPDATE_SET("Please re-enter the wallet password:", "Verify Password");

        private final String description;
        private final String okButtonText;

        PasswordRequirement(String description, String okButtonText) {
            this.description = description;
            this.okButtonText = okButtonText;
        }
    }
}
