package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class WalletImportEvent {
    private List<Wallet> wallets;

    public WalletImportEvent(Wallet wallet) {
        this.wallets = List.of(wallet);
    }

    public WalletImportEvent(List<Wallet> wallets) {
        this.wallets = wallets;
    }

    public List<Wallet> getWallets() {
        return wallets;
    }
}
