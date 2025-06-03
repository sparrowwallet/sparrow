package com.sparrowwallet.sparrow.joinstr;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import nostr.event.impl.GenericEvent;

public class NewPoolController extends JoinstrFormController {
    @FXML
    private TextField denominationField;

    @FXML
    private TextField peersField;

    @Override
    public void initializeView() {
    }

    @FXML
    private void handleCreateButton() {
        try {
            String denomination = denominationField.getText().trim();
            String peers = peersField.getText().trim();

            if (denomination.isEmpty() || peers.isEmpty()) {
                showError("Please enter denomination and peers to create a pool.");
                return;
            }

            try {
                double denominationValue = Double.parseDouble(denomination);
                if (denominationValue <= 0) {
                    showError("Denomination must be greater than 0");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Invalid denomination format");
                return;
            }

            try {
                int peersValue = Integer.parseInt(peers);
                if (peersValue <= 0) {
                    showError("Number of peers must be greater than 0");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Invalid number of peers format");
                return;
            }

            try {
                GenericEvent event = NostrPublisher.publishCustomEvent(denomination, peers);

                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setHeaderText(null);
                assert event != null;
                alert.setContentText("Pool created successfully!\nEvent ID: " + event.getId() +
                                     "\nDenomination: " + denomination + "\nPeers: " + peers);
                alert.showAndWait();
            } catch (Exception e) {
                showError("Error: " + e.getMessage());
            }

            denominationField.clear();
            peersField.clear();

        } catch (Exception e) {
            showError("An error occurred: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}