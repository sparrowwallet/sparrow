package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;

import java.util.List;

/**
 * This event is fired when a wallet entry (transaction, txi or txo) label is changed.
 * Extends WalletDataChangedEvent so triggers a background save.
 */
public class WalletEntryLabelsChangedEvent extends WalletDataChangedEvent {
    private final List<Entry> entries;

    public WalletEntryLabelsChangedEvent(Wallet wallet, Entry entry) {
        super(wallet);
        this.entries = List.of(entry);
    }

    public WalletEntryLabelsChangedEvent(Wallet wallet, List<Entry> entries) {
        super(wallet);
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
