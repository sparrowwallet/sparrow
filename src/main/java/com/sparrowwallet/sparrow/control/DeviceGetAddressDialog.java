package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.DeviceAddressEvent;
import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class DeviceGetAddressDialog extends DeviceDialog<Address> {
    public DeviceGetAddressDialog(List<String> operationFingerprints) {
        super(operationFingerprints);
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        return new DevicePane(DevicePane.DeviceOperation.GET_ADDRESS, device, defaultDevice);
    }

    @Subscribe
    public void deviceAddress(DeviceAddressEvent event) {
        setResult(event.getAddress());
    }
}
