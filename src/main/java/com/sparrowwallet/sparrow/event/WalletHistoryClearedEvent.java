package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryClearedEvent extends WalletSettingsChangedEvent {
    public WalletHistoryClearedEvent(Wallet wallet, Wallet pastWallet, String walletId) {
        super(wallet, pastWallet, walletId);
    }
}
