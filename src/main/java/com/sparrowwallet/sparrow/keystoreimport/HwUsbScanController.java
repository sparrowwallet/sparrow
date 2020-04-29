package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.external.Device;
import com.sparrowwallet.sparrow.external.Hwi;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.List;

public class HwUsbScanController extends KeystoreImportDetailController {
    @FXML
    private Label message;

    @FXML
    private Button scan;

    public void initializeView(String updateMessage) {
        message.setText(updateMessage);
    }

    public void scan(ActionEvent event) {
        message.setText("Please check your device");
        scan.setText("Scanning...");
        scan.setDisable(true);

        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(null);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            List<Device> devices = enumerateService.getValue();
            getMasterController().showUsbDevices(devices);
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            getMasterController().showUsbError(enumerateService.getException().getMessage());
        });
        enumerateService.start();
    }
}
