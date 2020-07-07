package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

import java.util.List;

public class SpendUtxoEvent {
    private final List<HashIndexEntry> utxoEntries;

    public SpendUtxoEvent(List<HashIndexEntry> utxoEntries) {
        this.utxoEntries = utxoEntries;
    }

    public List<HashIndexEntry> getUtxoEntries() {
        return utxoEntries;
    }
}
