package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;

import java.util.*;

/**
 * This event is fired when a wallet entry (transaction, txi or txo) label is changed.
 */
public class WalletEntryLabelsChangedEvent extends WalletChangedEvent {
    //Contains the changed entry mapped to the entry that changed it, if changed recursively (otherwise null)
    private final Map<Entry, Entry> entrySourceMap;
    private final boolean propagate;

    public WalletEntryLabelsChangedEvent(Wallet wallet, Entry entry) {
        this(wallet, List.of(entry));
    }

    public WalletEntryLabelsChangedEvent(Wallet wallet, List<Entry> entries) {
        this(wallet, entries, true);
    }

    public WalletEntryLabelsChangedEvent(Wallet wallet, List<Entry> entries, boolean propagate) {
        super(wallet);
        this.entrySourceMap = new LinkedHashMap<>();
        for(Entry entry : entries) {
            entrySourceMap.put(entry, null);
        }
        this.propagate = propagate;
    }

    public WalletEntryLabelsChangedEvent(Wallet wallet, Map<Entry, Entry> entrySourceMap) {
        super(wallet);
        this.entrySourceMap = entrySourceMap;
        this.propagate = true;
    }

    public Collection<Entry> getEntries() {
        return entrySourceMap.keySet();
    }

    public Entry getSource(Entry entry) {
        return entrySourceMap.get(entry);
    }

    public boolean propagate() {
        return propagate;
    }
}
