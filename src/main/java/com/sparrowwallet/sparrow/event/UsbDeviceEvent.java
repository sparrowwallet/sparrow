package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.io.Device;

import java.util.List;

public class UsbDeviceEvent {
    private final List<Device> devices;

    public UsbDeviceEvent(List<Device> devices) {
        this.devices = devices;
    }

    public List<Device> getDevices() {
        return devices;
    }
}
