package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

import java.util.List;

public class ChildWalletsAddedEvent extends WalletChangedEvent {
    private final Storage storage;
    private final List<Wallet> childWallets;

    public ChildWalletsAddedEvent(Storage storage, Wallet masterWallet, Wallet childWallet) {
        super(masterWallet);
        this.storage = storage;
        this.childWallets = List.of(childWallet);
    }

    public ChildWalletsAddedEvent(Storage storage, Wallet masterWallet, List<Wallet> childWallets) {
        super(masterWallet);
        this.storage = storage;
        this.childWallets = childWallets;
    }

    public Storage getStorage() {
        return storage;
    }

    public List<Wallet> getChildWallets() {
        return childWallets;
    }

    public String getMasterWalletId() {
        return storage.getWalletId(getWallet());
    }
}
