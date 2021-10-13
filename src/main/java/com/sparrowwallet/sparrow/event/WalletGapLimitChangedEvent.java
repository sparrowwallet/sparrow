package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is posted if the wallet's gap limit has changed, and triggers a history fetch for the new nodes.
 *
 */
public class WalletGapLimitChangedEvent extends WalletChangedEvent {
    public WalletGapLimitChangedEvent(Wallet wallet) {
        super(wallet);
    }

    public int getGapLimit() {
        return getWallet().getGapLimit();
    }
}
