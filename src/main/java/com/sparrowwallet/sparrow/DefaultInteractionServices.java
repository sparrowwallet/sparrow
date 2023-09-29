package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.control.KeystorePassphraseDialog;
import com.sparrowwallet.sparrow.control.TextUtils;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import org.controlsfx.control.HyperlinkLabel;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sparrowwallet.sparrow.AppServices.*;

public class DefaultInteractionServices implements InteractionServices {
    @Override
    public Optional<ButtonType> showAlert(String title, String content, Alert.AlertType alertType, Node graphic, ButtonType... buttons) {
        Alert alert = new Alert(alertType, content, buttons);
        alert.initOwner(getActiveWindow());
        setStageIcon(alert.getDialogPane().getScene().getWindow());
        alert.getDialogPane().getScene().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        alert.setTitle(title);
        alert.setHeaderText(title);
        if(graphic != null) {
            alert.setGraphic(graphic);
        }

        Pattern linkPattern = Pattern.compile("\\[(http.+)]");
        Matcher matcher = linkPattern.matcher(content);
        if(matcher.find()) {
            String link = matcher.group(1);
            HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(content);
            hyperlinkLabel.setMaxWidth(Double.MAX_VALUE);
            hyperlinkLabel.setMaxHeight(Double.MAX_VALUE);
            hyperlinkLabel.getStyleClass().add("content");
            Label label = new Label();
            hyperlinkLabel.setPrefWidth(Math.max(360, TextUtils.computeTextWidth(label.getFont(), link, 0.0D) + 50));
            hyperlinkLabel.setOnAction(event -> {
                alert.close();
                AppServices.get().getApplication().getHostServices().showDocument(link);
            });
            alert.getDialogPane().setContent(hyperlinkLabel);
        }

        String[] lines = content.split("\r\n|\r|\n");
        if(lines.length > 3 || org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.WINDOWS) {
            double numLines = Arrays.stream(lines).mapToDouble(line -> Math.ceil(TextUtils.computeTextWidth(Font.getDefault(), line, 0) / 300)).sum();
            alert.getDialogPane().setPrefHeight(200 + numLines * 20);
        }

        alert.setResizable(true);

        moveToActiveWindowScreen(alert);
        return alert.showAndWait();
    }

    @Override
    public Optional<String> requestPassphrase(String walletName, Keystore keystore) {
        KeystorePassphraseDialog passphraseDialog = new KeystorePassphraseDialog(walletName, keystore);
        passphraseDialog.initOwner(getActiveWindow());
        return passphraseDialog.showAndWait();
    }
}
