package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.io.Config;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

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

            long denominationSats = Long.valueOf((long) (Double.parseDouble(denomination)*100000000.0D));

            // TODO: Implement pool creation logic here
            JoinstrPool pool = new JoinstrPool(getWalletForm(), Config.get().getNostrRelay(),9999, "pubkey", denominationSats);
            pool.createPSBT();

            /*
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("Pool created successfully!");
            alert.showAndWait();
            */

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
