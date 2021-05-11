package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

import java.io.File;

/**
 * Event class used to request the wallet open dialog
 */
public class RequestWalletOpenEvent {
    private final Window window;
    private final File file;

    public RequestWalletOpenEvent(Window window) {
        this.window = window;
        this.file = null;
    }

    public RequestWalletOpenEvent(Window window, File file) {
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
