package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

import java.util.List;

public class SendActionEvent {
    private final List<HashIndexEntry> utxoEntries;

    public SendActionEvent(List<HashIndexEntry> utxoEntries) {
        this.utxoEntries = utxoEntries;
    }

    public List<HashIndexEntry> getUtxoEntries() {
        return utxoEntries;
    }
}
