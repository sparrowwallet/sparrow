package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletPasswordChangedEvent extends WalletSettingsChangedEvent {
    public WalletPasswordChangedEvent(Wallet wallet, Wallet pastWallet, String walletId) {
        super(wallet, pastWallet, walletId);
    }
}
