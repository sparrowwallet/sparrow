package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletMixConfigChangedEvent extends WalletChangedEvent {
    public WalletMixConfigChangedEvent(Wallet wallet) {
        super(wallet);
    }
}
