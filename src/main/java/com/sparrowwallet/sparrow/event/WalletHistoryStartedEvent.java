package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryStartedEvent extends WalletHistoryStatusEvent {
    public WalletHistoryStartedEvent(Wallet wallet) {
        super(wallet, true);
    }
}
