package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;

public class WalletEntryLabelChangedEvent extends WalletChangedEvent {
    private final Entry entry;

    public WalletEntryLabelChangedEvent(Wallet wallet, Entry entry) {
        super(wallet);
        this.entry = entry;
    }

    public Entry getEntry() {
        return entry;
    }
}
