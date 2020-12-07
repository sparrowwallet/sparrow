package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

/**
 * Event class used to request the QRScanDialog is opened
 */
public class RequestQRScanEvent {
    private final Window window;

    public RequestQRScanEvent(Window window) {
        this.window = window;
    }

    public Window getWindow() {
        return window;
    }
}
