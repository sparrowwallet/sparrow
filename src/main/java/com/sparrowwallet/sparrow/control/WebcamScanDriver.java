package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.WebcamDevice;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDevice;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDriver;

import java.util.ArrayList;
import java.util.List;

public class WebcamScanDriver extends WebcamDefaultDriver {
    @Override
    public List<WebcamDevice> getDevices() {
        List<WebcamDevice> devices = super.getDevices();
        List<WebcamDevice> scanDevices = new ArrayList<>();
        for(WebcamDevice device : devices) {
            WebcamDefaultDevice defaultDevice = (WebcamDefaultDevice)device;
            scanDevices.add(new WebcamScanDevice(defaultDevice.getDeviceRef()));
        }

        return scanDevices;
    }
}
