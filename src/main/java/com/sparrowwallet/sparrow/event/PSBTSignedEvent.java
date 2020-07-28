package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

/**
 * This event is used by the DeviceSignDialog to indicate that a USB device has signed a PSBT
 *
 */
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
