package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.control.DeviceAccordion;
import com.sparrowwallet.sparrow.external.Device;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;

import java.util.List;

public class UsbDevicesController extends KeystoreImportDetailController {
    @FXML
    private DeviceAccordion deviceAccordion;

    public void initializeView(List<Device> devices) {
        deviceAccordion.setDeviceOperation(DeviceAccordion.DeviceOperation.IMPORT);
        deviceAccordion.setDevices(getMasterController().getWallet(), FXCollections.observableList(devices));
    }
}
