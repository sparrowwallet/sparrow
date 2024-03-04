package com.sparrowwallet.sparrow.event;

import javafx.stage.Window;

import java.io.File;

public class RequestVerifyDownloadEvent {
    private final Window window;
    private final File file;

    public RequestVerifyDownloadEvent(Window window) {
        this.window = window;
        this.file = null;
    }

    public RequestVerifyDownloadEvent(Window window, File file) {
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
