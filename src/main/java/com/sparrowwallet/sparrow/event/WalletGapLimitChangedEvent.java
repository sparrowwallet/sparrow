package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

/**
 * This event is posted if the wallet's gap limit has changed, and triggers a history fetch for the new nodes.
 *
 */
public class WalletGapLimitChangedEvent extends WalletChangedEvent {
    private final String walletId;
    private final int previousGapLimit;

    public WalletGapLimitChangedEvent(String walletId, Wallet wallet, int previousGapLimit) {
        super(wallet);
        this.walletId = walletId;
        this.previousGapLimit = previousGapLimit;
    }

    public String getWalletId() {
        return walletId;
    }

    public int getGapLimit() {
        return getWallet().getGapLimit();
    }

    public int getPreviousGapLimit() {
        return previousGapLimit;
    }

    public int getPreviousLookAheadIndex(WalletNode node) {
        int lookAheadIndex = getPreviousGapLimit() - 1;
        Integer highestUsed = node.getHighestUsedIndex();
        if(highestUsed != null) {
            lookAheadIndex = highestUsed + getPreviousGapLimit();
        }

        return lookAheadIndex;
    }
}
