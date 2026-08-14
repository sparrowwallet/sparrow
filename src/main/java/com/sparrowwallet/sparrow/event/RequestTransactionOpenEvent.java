package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import javafx.stage.Window;

import java.io.File;

/**
 * Event class used to request the transaction open file dialog
 */
public class RequestTransactionOpenEvent {
    private final Window window;
    private final File file;
    private final PSBT contextPsbt;

    public RequestTransactionOpenEvent(Window window) {
        this(window, null, null);
    }

    public RequestTransactionOpenEvent(Window window, File file) {
        this(window, file, null);
    }

    public RequestTransactionOpenEvent(Window window, File file, PSBT contextPsbt) {
        this.window = window;
        this.file = file;
        this.contextPsbt = contextPsbt;
    }

    public Window getWindow() {
        return window;
    }

    public File getFile() {
        return file;
    }

    public PSBT getContextPsbt() {
        return contextPsbt;
    }
}
