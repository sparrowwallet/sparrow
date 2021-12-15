package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletDeletedEvent extends WalletChangedEvent {
    public WalletDeletedEvent(Wallet wallet) {
        super(wallet);
    }
}
