package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import static com.sparrowwallet.sparrow.AppServices.*;

public class BitBoxPairingDialog extends Alert {
    public BitBoxPairingDialog(String code) {
        super(AlertType.INFORMATION);
        initOwner(getActiveWindow());
        setStageIcon(getDialogPane().getScene().getWindow());
        getDialogPane().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        setTitle("Confirm BitBox02 Pairing");
        setHeaderText(getTitle());
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER);
        vBox.setPadding(new Insets(10, 20, 10, 20));
        Label instructions = new Label("Confirm the following code is shown on BitBox02");
        Label codeLabel = new Label(code);
        codeLabel.getStyleClass().add("fixed-width");
        vBox.getChildren().addAll(instructions, codeLabel);
        getDialogPane().setContent(vBox);
        moveToActiveWindowScreen(this);
        getDialogPane().getButtonTypes().clear();
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
