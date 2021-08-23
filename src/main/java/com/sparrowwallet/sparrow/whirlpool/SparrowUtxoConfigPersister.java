package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoConfigData;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoConfigPersisted;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoConfigPersister;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.UtxoMixData;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletUtxoMixesChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SparrowUtxoConfigPersister extends UtxoConfigPersister {
    private static final Logger log = LoggerFactory.getLogger(SparrowUtxoConfigPersister.class);

    private final String walletId;
    private long lastWrite;

    public SparrowUtxoConfigPersister(String walletId) {
        super(walletId);
        this.walletId = walletId;
    }

    @Override
    public synchronized UtxoConfigData load() throws Exception {
        Wallet wallet = getWallet();
        if(wallet == null) {
            throw new IllegalStateException("Can't find wallet with walletId " + walletId);
        }

        Map<String, UtxoConfigPersisted> utxoConfigs = wallet.getUtxoMixes().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), entry -> new UtxoConfigPersisted(entry.getValue().getPoolId(), entry.getValue().getMixesDone(), entry.getValue().getForwarding()),
                        (u, v) -> { throw new IllegalStateException("Duplicate utxo config hashes"); },
                        HashMap::new));

        return new UtxoConfigData(utxoConfigs);
    }

    @Override
    public synchronized void write(UtxoConfigData data) throws Exception {
        Wallet wallet = getWallet();
        if(wallet == null) {
            //Wallet is already closed
            return;
        }

        Map<String, UtxoConfigPersisted> currentData = new HashMap<>(data.getUtxoConfigs());
        Map<Sha256Hash, UtxoMixData> changedUtxoMixes = currentData.entrySet().stream()
                .collect(Collectors.toMap(entry -> Sha256Hash.wrap(entry.getKey()), entry -> new UtxoMixData(entry.getValue().getPoolId(), entry.getValue().getMixsDone(), entry.getValue().getForwarding()),
                (u, v) -> { throw new IllegalStateException("Duplicate utxo config hashes"); },
                HashMap::new));

        MapDifference<Sha256Hash, UtxoMixData> mapDifference = Maps.difference(changedUtxoMixes, wallet.getUtxoMixes());
        Map<Sha256Hash, UtxoMixData> removedUtxoMixes = mapDifference.entriesOnlyOnRight();
        wallet.getUtxoMixes().putAll(changedUtxoMixes);
        wallet.getUtxoMixes().keySet().removeAll(removedUtxoMixes.keySet());

        EventManager.get().post(new WalletUtxoMixesChangedEvent(wallet, changedUtxoMixes, removedUtxoMixes));
        lastWrite = System.currentTimeMillis();
    }

    private Wallet getWallet() {
        return AppServices.get().getOpenWallets().entrySet().stream().filter(entry -> entry.getValue().getWalletId(entry.getKey()).equals(walletId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    @Override
    public long getLastWrite() {
        return lastWrite;
    }
}
