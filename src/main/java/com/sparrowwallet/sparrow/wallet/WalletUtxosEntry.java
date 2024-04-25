package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.io.Config;

import java.util.*;
import java.util.stream.Collectors;

public class WalletUtxosEntry extends Entry {
    public static final int DUST_ATTACK_THRESHOLD_SATS = 1000;

    public WalletUtxosEntry(Wallet wallet) {
        super(wallet, wallet.getName(), wallet.getWalletUtxos().entrySet().stream().map(entry -> new UtxoEntry(entry.getValue().getWallet(), entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList()));
        calculateDuplicates();
        calculateDust();
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

    protected void calculateDust() {
        long dustAttackThreshold = Config.get().getDustAttackThreshold();
        Set<WalletNode> duplicateNodes = getWallet().getWalletTxos().values().stream()
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                .entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());

        for(Entry entry : getChildren()) {
            UtxoEntry utxoEntry = (UtxoEntry) entry;
            utxoEntry.setDustAttack(utxoEntry.getValue() <= dustAttackThreshold && duplicateNodes.contains(utxoEntry.getNode()) && !utxoEntry.getWallet().allInputsFromWallet(utxoEntry.getHashIndex().getHash()));
        }
    }

    public void updateUtxos() {
        List<Entry> current = getWallet().getWalletUtxos().entrySet().stream().map(entry -> new UtxoEntry(entry.getValue().getWallet(), entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());

        List<Entry> entriesAdded = new ArrayList<>(current);
        entriesAdded.removeAll(previous);
        getChildren().addAll(entriesAdded);

        List<Entry> entriesRemoved = new ArrayList<>(previous);
        entriesRemoved.removeAll(current);
        getChildren().removeAll(entriesRemoved);

        calculateDuplicates();
        calculateDust();
    }

    public long getBalance() {
        return getChildren().stream().mapToLong(Entry::getValue).sum();
    }

    public long getMempoolBalance() {
        return getChildren().stream().filter(entry -> ((UtxoEntry)entry).getHashIndex().getHeight() <= 0).mapToLong(Entry::getValue).sum();
    }
}
