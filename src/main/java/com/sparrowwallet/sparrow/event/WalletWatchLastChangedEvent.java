package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletWatchLastChangedEvent extends WalletSettingsChangedEvent {
    private final Integer watchLast;

    public WalletWatchLastChangedEvent(Wallet wallet, Wallet pastWallet, String walletId, Integer watchLast) {
        super(wallet, pastWallet, walletId);
        this.watchLast = watchLast;
    }

    public Integer getWatchLast() {
        return watchLast;
    }
}
