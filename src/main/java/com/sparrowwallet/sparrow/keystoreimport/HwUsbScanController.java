package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.application.Platform;
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
            if(devices.isEmpty()) {
                getMasterController().showUsbNone();
            } else {
                getMasterController().showUsbDevices(devices);
            }
            Platform.runLater(() -> EventManager.get().post(new UsbDeviceEvent(devices)));
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            getMasterController().showUsbError(enumerateService.getException().getMessage());
        });
        enumerateService.start();
    }
}
