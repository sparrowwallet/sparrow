package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.external.Device;
import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;

public class DeviceAccordion extends Accordion {
    private ObservableList<Device> devices;
    private DeviceOperation deviceOperation = DeviceOperation.IMPORT;

    public void setDevices(Wallet wallet, ObservableList<Device> devices) {
        this.devices = devices;

        for(Device device : devices) {
            DevicePane devicePane = new DevicePane(this, wallet, device);
            this.getPanes().add(devicePane);
        }
    }

    public DeviceOperation getDeviceOperation() {
        return deviceOperation;
    }

    public void setDeviceOperation(DeviceOperation deviceOperation) {
        this.deviceOperation = deviceOperation;
    }

    public enum DeviceOperation {
        IMPORT;
    }
}
