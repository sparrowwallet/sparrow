package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.client.indexHandler.AbstractIndexHandler;
import com.sparrowwallet.drongo.wallet.WalletNode;

public class SparrowIndexHandler extends AbstractIndexHandler {
    private final WalletNode walletNode;
    private final int defaultValue;

    public SparrowIndexHandler(WalletNode walletNode) {
        this(walletNode, 0);
    }

    public SparrowIndexHandler(WalletNode walletNode, int defaultValue) {
        this.walletNode = walletNode;
        this.defaultValue = defaultValue;
    }

    @Override
    public synchronized int get() {
        Integer currentIndex = walletNode.getHighestUsedIndex();
        return currentIndex == null ? defaultValue : currentIndex + 1;
    }

    @Override
    public synchronized int getAndIncrement() {
        return get();
    }

    @Override
    public synchronized void set(int value) {
    }
}
