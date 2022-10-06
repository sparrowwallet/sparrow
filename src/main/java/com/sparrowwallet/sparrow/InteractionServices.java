package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.wallet.Keystore;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public interface InteractionServices {
    Optional<ButtonType> showAlert(String title, String content, Alert.AlertType alertType, Node graphic, ButtonType... buttons);
    Optional<String> requestPassphrase(String walletName, Keystore keystore);
}
