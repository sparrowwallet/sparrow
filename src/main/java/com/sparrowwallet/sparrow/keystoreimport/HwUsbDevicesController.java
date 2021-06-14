package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.sparrow.control.DevicePane;
import com.sparrowwallet.sparrow.io.Device;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;

import java.util.List;

public class HwUsbDevicesController extends KeystoreImportDetailController {
    @FXML
    private Accordion deviceAccordion;

    public void initializeView(List<Device> devices) {
        for(Device device : devices) {
            DevicePane devicePane = new DevicePane(getMasterController().getWallet(), device, devices.size() == 1);
            deviceAccordion.getPanes().add(devicePane);
        }
    }
}
