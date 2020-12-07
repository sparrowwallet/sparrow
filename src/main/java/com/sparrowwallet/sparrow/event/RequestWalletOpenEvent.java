package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

/**
 * Event class used to request the wallet open dialog
 */
public class RequestWalletOpenEvent {
    private final Window window;

    public RequestWalletOpenEvent(Window window) {
        this.window = window;
    }

    public Window getWindow() {
        return window;
    }
}
