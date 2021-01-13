package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

public class WalletOpeningEvent {
    private final Storage storage;
    private final Wallet wallet;

    public WalletOpeningEvent(Storage storage, Wallet wallet) {
        this.storage = storage;
        this.wallet = wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
