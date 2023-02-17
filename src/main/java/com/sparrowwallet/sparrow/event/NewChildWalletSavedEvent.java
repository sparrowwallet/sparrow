package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

public class NewChildWalletSavedEvent {
    private final Storage storage;
    private final Wallet masterWallet;
    private final Wallet childWallet;

    public NewChildWalletSavedEvent(Storage storage, Wallet masterWallet, Wallet childWallet) {
        this.storage = storage;
        this.masterWallet = masterWallet;
        this.childWallet = childWallet;
    }

    public String getMasterWalletId() {
        return storage.getWalletId(masterWallet);
    }

    public Wallet getChildWallet() {
        return childWallet;
    }
}
