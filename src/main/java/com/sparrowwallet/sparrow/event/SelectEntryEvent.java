package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;

public class SelectEntryEvent {
    private final Entry entry;

    public SelectEntryEvent(Entry entry) {
        this.entry = entry;
    }

    public Entry getEntry() {
        return entry;
    }

    public Wallet getWallet() {
        return entry.getWallet();
    }
}
