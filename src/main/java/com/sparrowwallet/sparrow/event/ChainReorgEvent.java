package com.sparrowwallet.sparrow.event;

/**
 * Posted once the verified header chain has been rewound to a fork point and the connected server's headers adopted above it. Every open wallet
 * invalidates the cached status of the nodes the fork affects and refreshes, so that heights proven against a header that is no longer on the chain
 * are proven again.
 * <p>
 * This is dispatched on the thread that synced the headers - a wallet history thread as often as the header sync service - while it holds the sync
 * lock, so a handler must hop to the application thread itself rather than doing anything lengthy here.
 */
public class ChainReorgEvent {
    private final int forkHeight;

    public ChainReorgEvent(int forkHeight) {
        this.forkHeight = forkHeight;
    }

    public int getForkHeight() {
        return forkHeight;
    }
}
