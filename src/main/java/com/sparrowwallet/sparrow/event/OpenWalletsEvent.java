package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class OpenWalletsEvent {
    private final List<Wallet> wallets;

    public OpenWalletsEvent(List<Wallet> wallets) {
        this.wallets = wallets;
    }

    public List<Wallet> getWallets() {
        return wallets;
    }
}
