package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;

/**
 * This event is fired when a wallet entry (transaction, txi or txo) label is changed.
 * Extends WalletDataChangedEvent so triggers a background save.
 */
public class WalletEntryLabelChangedEvent extends WalletDataChangedEvent {
    private final Entry entry;

    public WalletEntryLabelChangedEvent(Wallet wallet, Entry entry) {
        super(wallet);
        this.entry = entry;
    }

    public Entry getEntry() {
        return entry;
    }
}
