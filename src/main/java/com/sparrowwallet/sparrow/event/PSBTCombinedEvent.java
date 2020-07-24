package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

public class PSBTCombinedEvent extends PSBTEvent {
    public PSBTCombinedEvent(PSBT psbt) {
        super(psbt);
    }
}
