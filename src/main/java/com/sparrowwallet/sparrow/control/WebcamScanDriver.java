package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.WebcamDevice;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDevice;
import com.github.sarxos.webcam.ds.buildin.WebcamDefaultDriver;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class WebcamScanDriver extends WebcamDefaultDriver {
    private static final ObservableList<WebcamDevice> webcamDevices = FXCollections.observableArrayList();
    private static boolean rescan;

    @Override
    public List<WebcamDevice> getDevices() {
        if(rescan || webcamDevices.isEmpty()) {
            List<WebcamDevice> devices = super.getDevices();
            List<WebcamDevice> scanDevices = new ArrayList<>();
            for(WebcamDevice device : devices) {
                WebcamDefaultDevice defaultDevice = (WebcamDefaultDevice)device;
                WebcamScanDevice scanDevice = new WebcamScanDevice(defaultDevice.getDeviceRef());
                if(scanDevices.stream().noneMatch(dev -> ((WebcamScanDevice)dev).getDeviceName().equals(scanDevice.getDeviceName()))) {
                    scanDevices.add(scanDevice);
                }
            }

            List<WebcamDevice> newDevices = new ArrayList<>(scanDevices);
            newDevices.removeAll(webcamDevices);
            webcamDevices.addAll(newDevices);
            webcamDevices.removeIf(device -> !scanDevices.contains(device));
        }

        return webcamDevices;
    }

    public static ObservableList<WebcamDevice> getFoundDevices() {
        return webcamDevices;
    }

    public static void rescan() {
        rescan = true;
    }
}
