package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryFinishedEvent extends WalletHistoryStatusEvent {
    public WalletHistoryFinishedEvent(Wallet wallet) {
        super(wallet, false);
    }

    public WalletHistoryFinishedEvent(Wallet wallet, String errorMessage) {
        super(wallet, errorMessage);
    }
}
