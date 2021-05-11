package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

import java.io.File;

/**
 * Event class used to request the transaction open file dialog
 */
public class RequestTransactionOpenEvent {
    private final Window window;
    private final File file;

    public RequestTransactionOpenEvent(Window window) {
        this.window = window;
        this.file = null;
    }

    public RequestTransactionOpenEvent(Window window, File file) {
        this.window = window;
        this.file = file;
    }

    public Window getWindow() {
        return window;
    }

    public File getFile() {
        return file;
    }
}
