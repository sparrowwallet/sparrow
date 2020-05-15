package com.sparrowwallet.sparrow;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;

import java.io.File;

public class WalletTabData extends TabData {
    private Wallet wallet;
    private final File walletFile;

    public WalletTabData(TabType type, Wallet wallet, File walletFile) {
        super(type);
        this.wallet = wallet;
        this.walletFile = walletFile;

        EventManager.get().register(this);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public File getWalletFile() {
        return walletFile;
    }

    @Subscribe
    public void walletChanged(WalletChangedEvent event) {
        if(event.getWalletFile().equals(walletFile)) {
            wallet = event.getWallet();
        }
    }
}
