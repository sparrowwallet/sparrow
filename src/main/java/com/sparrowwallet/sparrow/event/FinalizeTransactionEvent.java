package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;

public class FinalizeTransactionEvent {
    private final PSBT psbt;
    private final Wallet signingWallet;

    public FinalizeTransactionEvent(PSBT psbt, Wallet signingWallet) {
        this.psbt = psbt;
        this.signingWallet = signingWallet;
    }

    public PSBT getPsbt() {
        return psbt;
    }

    public Wallet getSigningWallet() {
        return signingWallet;
    }
}
