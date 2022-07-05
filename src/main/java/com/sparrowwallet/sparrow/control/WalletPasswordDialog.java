package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
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
    private final CheckBox changePassword;
    private final CheckBox deleteBackups;
    private boolean addingPassword;

    public WalletPasswordDialog(String walletName, PasswordRequirement requirement) {
        this(walletName, requirement, false);
    }

    public WalletPasswordDialog(String walletName, PasswordRequirement requirement, boolean suggestChangePassword) {
        this.requirement = requirement;
        this.password = new ViewPasswordField();
        this.passwordConfirm = new ViewPasswordField();
        this.backupExisting = new CheckBox("Backup existing wallet first");
        this.changePassword = new CheckBox("Change password");
        this.deleteBackups = new CheckBox("Delete any backups");

        final DialogPane dialogPane = getDialogPane();
        setTitle("Wallet Password" + (walletName != null ? " - " + walletName : ""));
        dialogPane.setHeaderText(walletName != null ? requirement.description.substring(0, requirement.description.length() - 1) + " for " + walletName + ":" : requirement.description);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(260);
        AppServices.moveToActiveWindowScreen(this);

        Glyph lock = new Glyph("FontAwesome", FontAwesome.Glyph.LOCK);
        lock.setFontSize(50);
        dialogPane.setGraphic(lock);

        final VBox content = new VBox(10);
        content.setPrefHeight(100);
        content.getChildren().add(password);
        content.getChildren().add(passwordConfirm);

        if(requirement == PasswordRequirement.UPDATE_SET) {
            content.getChildren().add(changePassword);
            changePassword.selectedProperty().addListener((observable, oldValue, newValue) -> {
                backupExisting.setVisible(!newValue);
            });
            changePassword.setSelected(suggestChangePassword);
        }

        if(requirement == PasswordRequirement.UPDATE_EMPTY || requirement == PasswordRequirement.UPDATE_SET) {
            backupExisting.managedProperty().bind(backupExisting.visibleProperty());
            deleteBackups.managedProperty().bind(deleteBackups.visibleProperty());
            deleteBackups.visibleProperty().bind(backupExisting.visibleProperty().not());
            content.getChildren().addAll(backupExisting, deleteBackups);
            backupExisting.setSelected(true);
            deleteBackups.setSelected(true);
        }

        dialogPane.setContent(content);

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater( () -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(passwordConfirm, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Password confirmation does not match", !passwordConfirm.getText().equals(password.getText())));
        });

        okButtonType = new javafx.scene.control.ButtonType(requirement.okButtonText, ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        okButton.setPrefWidth(130);
        BooleanBinding isInvalid = Bindings.createBooleanBinding(() -> (requirement == PasswordRequirement.LOAD && password.getText().isEmpty()) || (passwordConfirm.isVisible() && !password.getText().equals(passwordConfirm.getText())), password.textProperty(), passwordConfirm.textProperty());
        okButton.disableProperty().bind(isInvalid);

        if(requirement != PasswordRequirement.UPDATE_NEW && requirement != PasswordRequirement.UPDATE_CHANGE) {
            passwordConfirm.setVisible(false);
            passwordConfirm.setManaged(false);
        }

        if(requirement == PasswordRequirement.UPDATE_NEW || requirement == PasswordRequirement.UPDATE_EMPTY || requirement == PasswordRequirement.UPDATE_CHANGE) {
            password.textProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue.isEmpty()) {
                    okButton.setText("No Password");
                    passwordConfirm.setVisible(false);
                    passwordConfirm.setManaged(false);
                    backupExisting.setVisible(true);
                    addingPassword = false;
                } else {
                    okButton.setText("Set Password");
                    passwordConfirm.setVisible(true);
                    passwordConfirm.setManaged(true);
                    backupExisting.setVisible(false);
                    addingPassword = true;
                }
            });
        }

        password.setPromptText("Password");
        Platform.runLater(password::requestFocus);
        passwordConfirm.setPromptText("Password Confirmation");

        setResultConverter(dialogButton -> dialogButton == okButtonType ? new SecureString(password.getText()) : null);
    }

    public boolean isBackupExisting() {
        return !(addingPassword || isChangePassword()) && backupExisting.isSelected();
    }

    public boolean isChangePassword() {
        return changePassword.isSelected();
    }

    public boolean isDeleteBackups() {
        return (addingPassword || isChangePassword()) && deleteBackups.isSelected();
    }

    public enum PasswordRequirement {
        LOAD("Enter the wallet password:", "Unlock"),
        UPDATE_NEW("Add a password to the wallet?\nLeave empty for no password:", "No Password"),
        UPDATE_EMPTY("This wallet has no password.\nAdd a password to the wallet?\nLeave empty for no password:", "No Password"),
        UPDATE_SET("Re-enter the wallet password:", "Verify Password"),
        UPDATE_CHANGE("Enter the new wallet password.\nLeave empty for no password:", "No Password");

        private final String description;
        private final String okButtonText;

        PasswordRequirement(String description, String okButtonText) {
            this.description = description;
            this.okButtonText = okButtonText;
        }
    }
}
