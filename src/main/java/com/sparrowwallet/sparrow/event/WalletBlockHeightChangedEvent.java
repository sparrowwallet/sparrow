package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is posted if the wallet's stored block height has changed.
 *
 */
public class WalletBlockHeightChangedEvent extends WalletChangedEvent {
    private final Integer blockHeight;

    public WalletBlockHeightChangedEvent(Wallet wallet, Integer blockHeight) {
        super(wallet);
        this.blockHeight = blockHeight;
    }

    public Integer getBlockHeight() {
        return blockHeight;
    }
}
