package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletBlockHeightChangedEvent extends WalletChangedEvent {
    private Integer blockHeight;

    public WalletBlockHeightChangedEvent(Wallet wallet, Integer blockHeight) {
        super(wallet);
        this.blockHeight = blockHeight;
    }

    public Integer getBlockHeight() {
        return blockHeight;
    }
}
