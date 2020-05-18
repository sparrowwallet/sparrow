package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;
import com.sparrowwallet.sparrow.io.Storage;

public class WalletTabData extends TabData {
    private Wallet wallet;
    private final Storage storage;

    public WalletTabData(TabType type, Wallet wallet, Storage storage) {
        super(type);
        this.wallet = wallet;
        this.storage = storage;

        EventManager.get().register(this);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    @Subscribe
    public void walletChanged(WalletChangedEvent event) {
        if(event.getWalletFile().equals(storage.getWalletFile())) {
            wallet = event.getWallet();
        }
    }
}
