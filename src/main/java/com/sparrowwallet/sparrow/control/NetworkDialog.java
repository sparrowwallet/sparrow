package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Network;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import java.util.Arrays;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.ComboBox;
import org.controlsfx.glyphfont.Glyph;

public class NetworkDialog extends Dialog<Network> {
    private final ComboBox<Network> networks;

    public NetworkDialog() {
        this.networks = new ComboBox<>();
        networks.getItems().addAll(Arrays.asList(Network.values()));
        networks.getSelectionModel().selectFirst();
        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        setTitle("Network");
        dialogPane.setHeaderText("Choose a network:");
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(200);

        Glyph wallet = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PROJECT_DIAGRAM);
        wallet.setFontSize(50);
        dialogPane.setGraphic(wallet);

        final VBox content = new VBox(10);
        content.getChildren().add(networks);

        dialogPane.setContent(content);

        final ButtonType okButtonType = new javafx.scene.control.ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(okButtonType);

        Platform.runLater(networks::requestFocus);
        setResultConverter(dialogButton -> dialogButton == okButtonType ? networks.getValue() : null);
    }
}
