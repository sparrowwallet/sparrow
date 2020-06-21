package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is posted if the wallet block height has changed.
 * Note that it is not posted if the wallet history has also changed - this event is used mainly to ensure the new block height is saved
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
