package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.transaction.TransactionView;
import javafx.stage.Window;

import java.io.File;

public class ViewPSBTEvent {
    private final Window window;
    private final String label;
    private final File file;
    private final PSBT psbt;
    private final TransactionView initialView;
    private final Integer initialIndex;

    public ViewPSBTEvent(Window window, String label, File file, PSBT psbt) {
        this(window, label, file, psbt, TransactionView.HEADERS, null);
    }

    public ViewPSBTEvent(Window window, String label, File file, PSBT psbt, TransactionView initialView, Integer initialIndex) {
        this.window = window;
        this.label = label;
        this.file = file;
        this.psbt = psbt;
        this.initialView = initialView;
        this.initialIndex = initialIndex;
    }

    public Window getWindow() {
        return window;
    }

    public String getLabel() {
        return label;
    }

    public File getFile() {
        return file;
    }

    public PSBT getPsbt() {
        return psbt;
    }

    public TransactionView getInitialView() {
        return initialView;
    }

    public Integer getInitialIndex() {
        return initialIndex;
    }
}
