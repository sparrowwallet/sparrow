package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

public class ChildWalletAddedEvent extends WalletChangedEvent {
    private final Storage storage;
    private final Wallet childWallet;

    public ChildWalletAddedEvent(Storage storage, Wallet masterWallet, Wallet childWallet) {
        super(masterWallet);
        this.storage = storage;
        this.childWallet = childWallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public Wallet getChildWallet() {
        return childWallet;
    }

    public String getMasterWalletId() {
        return storage.getWalletId(getWallet());
    }
}
