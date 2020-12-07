package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

/**
 * Event class used to request the transaction open file dialog
 */
public class RequestTransactionOpenEvent {
    private final Window window;

    public RequestTransactionOpenEvent(Window window) {
        this.window = window;
    }

    public Window getWindow() {
        return window;
    }
}
