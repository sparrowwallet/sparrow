package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletLabelChangedEvent extends WalletChangedEvent {
    public WalletLabelChangedEvent(Wallet wallet) {
        super(wallet);
    }

    public String getLabel() {
        return getWallet().getLabel();
    }
}
