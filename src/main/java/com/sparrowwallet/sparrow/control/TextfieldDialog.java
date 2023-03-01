package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextfieldDialog extends Dialog<String> {
    private static final Logger log = LoggerFactory.getLogger(TextAreaDialog.class);

    private final TextField textField;
    private final String defaultValue;

    public TextfieldDialog() {
        this("");
    }

    public TextfieldDialog(String defaultValue) {
        this(defaultValue, true);
    }

    public TextfieldDialog(@NamedArg("defaultValue") String defaultValue, @NamedArg("editable") boolean editable) {
        final DialogPane dialogPane = getDialogPane();
        setDialogPane(dialogPane);

        Image image = new Image("/image/sparrow-small.png");
        dialogPane.setGraphic(new ImageView(image));

        HBox hbox = new HBox();
        this.textField = new TextField(defaultValue);
        this.textField.setMaxWidth(Double.MAX_VALUE);
        this.textField.setEditable(editable);
        hbox.getChildren().add(textField);
        HBox.setHgrow(this.textField, Priority.ALWAYS);

        this.defaultValue = defaultValue;

        dialogPane.setContent(hbox);
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        dialogPane.getStyleClass().add("text-input-dialog");
        dialogPane.getButtonTypes().add(ButtonType.OK);
        if(editable) {
            dialogPane.getButtonTypes().add(ButtonType.CANCEL);
        }

        Platform.runLater(textField::requestFocus);

        setResultConverter((dialogButton) -> {
            ButtonBar.ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonBar.ButtonData.OK_DONE ? textField.getText() : null;
        });

        dialogPane.setPrefWidth(600);
        dialogPane.setPrefHeight(230);
        AppServices.moveToActiveWindowScreen(this);
    }

    public final TextField getEditor() {
        return textField;
    }

    public final String getDefaultValue() {
        return defaultValue;
    }
}
