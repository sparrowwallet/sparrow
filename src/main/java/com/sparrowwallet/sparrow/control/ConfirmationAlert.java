package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import static com.sparrowwallet.sparrow.AppServices.getActiveWindow;
import static com.sparrowwallet.sparrow.AppServices.setStageIcon;

public class ConfirmationAlert extends Alert {
    private final CheckBox dontAskAgain;

    public ConfirmationAlert(String title, String contentText, ButtonType... buttons) {
        super(AlertType.CONFIRMATION, contentText, buttons);

        initOwner(getActiveWindow());
        setStageIcon(getDialogPane().getScene().getWindow());
        getDialogPane().getScene().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        setTitle(title);
        setHeaderText(title);

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(10, 20, 10, 20));
        Label contentLabel = new Label(contentText);
        contentLabel.setWrapText(true);
        dontAskAgain = new CheckBox("Don't ask again");
        contentBox.getChildren().addAll(contentLabel, dontAskAgain);

        getDialogPane().setContent(contentBox);
    }

    public boolean isDontAskAgain() {
        return dontAskAgain.isSelected();
    }
}
