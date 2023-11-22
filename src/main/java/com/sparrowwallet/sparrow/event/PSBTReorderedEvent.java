package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

public class PSBTReorderedEvent {
    private final PSBT psbt;

    public PSBTReorderedEvent(PSBT psbt) {
        this.psbt = psbt;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
