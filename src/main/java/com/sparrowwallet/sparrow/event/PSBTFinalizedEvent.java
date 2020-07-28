package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

/**
 * This event is fired once the signing animation has finished and the PSBT has been finalized
 *
 */
public class PSBTFinalizedEvent extends PSBTEvent {
    public PSBTFinalizedEvent(PSBT psbt) {
        super(psbt);
    }
}
