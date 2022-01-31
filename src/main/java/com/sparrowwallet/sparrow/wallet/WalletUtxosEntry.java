package com.sparrowwallet.sparrow.wallet;

import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;

import java.util.*;
import java.util.stream.Collectors;

public class WalletUtxosEntry extends Entry {
    public WalletUtxosEntry(Wallet wallet) {
        super(wallet, wallet.getName(), wallet.getWalletUtxos().entrySet().stream().map(entry -> new UtxoEntry(wallet, entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList()));
        calculateDuplicates();
        updateMixProgress();
    }

    @Override
    public Long getValue() {
        return 0L;
    }

    @Override
    public String getEntryType() {
        return "Wallet UTXOs";
    }

    @Override
    public Function getWalletFunction() {
        return Function.UTXOS;
    }

    protected void calculateDuplicates() {
        Map<String, UtxoEntry> addressMap = new HashMap<>();

        for(Entry entry : getChildren()) {
            UtxoEntry utxoEntry = (UtxoEntry)entry;
            String address = utxoEntry.getAddress().toString();

            UtxoEntry duplicate = addressMap.get(address);
            if(duplicate != null) {
                duplicate.setDuplicateAddress(true);
                utxoEntry.setDuplicateAddress(true);
            } else {
                addressMap.put(address, utxoEntry);
                utxoEntry.setDuplicateAddress(false);
            }
        }
    }

    public void updateMixProgress() {
        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(getWallet());
        if(whirlpool != null) {
            for(Entry entry : getChildren()) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                MixProgress mixProgress = whirlpool.getMixProgress(utxoEntry.getHashIndex());
                if(mixProgress != null || utxoEntry.getMixStatus() == null || (utxoEntry.getMixStatus().getMixFailReason() == null && utxoEntry.getMixStatus().getNextMixUtxo() == null)) {
                    utxoEntry.setMixProgress(mixProgress);
                }
            }
        }
    }

    public void updateUtxos() {
        List<Entry> current = getWallet().getWalletUtxos().entrySet().stream().map(entry -> new UtxoEntry(getWallet(), entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());

        List<Entry> entriesAdded = new ArrayList<>(current);
        entriesAdded.removeAll(previous);
        getChildren().addAll(entriesAdded);

        List<Entry> entriesRemoved = new ArrayList<>(previous);
        entriesRemoved.removeAll(current);
        getChildren().removeAll(entriesRemoved);

        calculateDuplicates();
        updateMixProgress();
    }
}
