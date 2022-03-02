package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * The base class for all wallet events
 */
public class WalletChangedEvent {
    private final Wallet wallet;

    public WalletChangedEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean fromThisOrNested(Wallet targetWallet) {
        if(wallet.equals(targetWallet)) {
            return true;
        }

        return wallet.isNested() && targetWallet.getChildWallets().contains(wallet);
    }

    public boolean toThisOrNested(Wallet targetWallet) {
        if(wallet.equals(targetWallet)) {
            return true;
        }

        return targetWallet.isNested() && wallet.getChildWallets().contains(targetWallet);
    }
}
