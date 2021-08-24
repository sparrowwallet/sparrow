package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.UtxoMixData;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Map;

public class WalletUtxoMixesChangedEvent extends WalletChangedEvent {
    private final Map<Sha256Hash, UtxoMixData> changedUtxoMixes;
    private final Map<Sha256Hash, UtxoMixData> removedUtxoMixes;

    public WalletUtxoMixesChangedEvent(Wallet wallet, Map<Sha256Hash, UtxoMixData> changedUtxoMixes, Map<Sha256Hash, UtxoMixData> removedUtxoMixes) {
        super(wallet);
        this.changedUtxoMixes = changedUtxoMixes;
        this.removedUtxoMixes = removedUtxoMixes;
    }

    public Map<Sha256Hash, UtxoMixData> getChangedUtxoMixes() {
        return changedUtxoMixes;
    }

    public Map<Sha256Hash, UtxoMixData> getRemovedUtxoMixes() {
        return removedUtxoMixes;
    }
}
