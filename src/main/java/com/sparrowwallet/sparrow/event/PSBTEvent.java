package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

public class PSBTEvent {
    private final PSBT psbt;

    public PSBTEvent(PSBT psbt) {
        this.psbt = psbt;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
