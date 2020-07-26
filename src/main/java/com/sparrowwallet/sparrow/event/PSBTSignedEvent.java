package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

public class PSBTSignedEvent extends PSBTEvent {
    private final PSBT signedPsbt;

    public PSBTSignedEvent(PSBT psbt, PSBT signedPsbt) {
        super(psbt);
        this.signedPsbt = signedPsbt;
    }

    public PSBT getSignedPsbt() {
        return signedPsbt;
    }
}
