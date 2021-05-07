package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.net.Aopp;

public class ReceiveProofEvent {
    private final Wallet wallet;
    private final Aopp aopp;

    public ReceiveProofEvent(Wallet wallet, Aopp aopp) {
        this.wallet = wallet;
        this.aopp = aopp;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Aopp getAopp() {
        return aopp;
    }
}
