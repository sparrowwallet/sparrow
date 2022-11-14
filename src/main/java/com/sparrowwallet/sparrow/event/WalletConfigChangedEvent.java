package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletConfigChangedEvent extends WalletChangedEvent {
    public WalletConfigChangedEvent(Wallet wallet) {
        super(wallet);
    }
}
