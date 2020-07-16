package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.transaction.TransactionView;

public class ViewPSBTEvent {
    private final String label;
    private final PSBT psbt;
    private final TransactionView initialView;
    private final Integer initialIndex;

    public ViewPSBTEvent(String label, PSBT psbt) {
        this(label, psbt, TransactionView.HEADERS, null);
    }

    public ViewPSBTEvent(String label, PSBT psbt, TransactionView initialView, Integer initialIndex) {
        this.label = label;
        this.psbt = psbt;
        this.initialView = initialView;
        this.initialIndex = initialIndex;
    }

    public String getLabel() {
        return label;
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
