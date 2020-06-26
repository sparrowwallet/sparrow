package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.*;
import java.util.stream.Collectors;

public class WalletUtxosEntry extends Entry {
    private final Wallet wallet;

    public WalletUtxosEntry(Wallet wallet) {
        super(wallet.getName(), getWalletUtxos(wallet).entrySet().stream().map(entry -> new UtxoEntry(wallet, entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList()));
        this.wallet = wallet;
        calculateDuplicates();
    }

    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public Long getValue() {
        return 0L;
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

    public void updateUtxos() {
        List<Entry> current = getWalletUtxos(wallet).entrySet().stream().map(entry -> new UtxoEntry(wallet, entry.getKey(), HashIndexEntry.Type.OUTPUT, entry.getValue())).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());

        List<Entry> entriesAdded = new ArrayList<>(current);
        entriesAdded.removeAll(previous);
        getChildren().addAll(entriesAdded);

        List<Entry> entriesRemoved = new ArrayList<>(previous);
        entriesRemoved.removeAll(current);
        getChildren().removeAll(entriesRemoved);

        calculateDuplicates();
    }

    private static Map<BlockTransactionHashIndex, WalletNode> getWalletUtxos(Wallet wallet) {
        Map<BlockTransactionHashIndex, WalletNode> walletUtxos = new TreeMap<>();

        getWalletUtxos(wallet, walletUtxos, wallet.getNode(KeyPurpose.RECEIVE));
        getWalletUtxos(wallet, walletUtxos, wallet.getNode(KeyPurpose.CHANGE));

        return walletUtxos;
    }

    private static void getWalletUtxos(Wallet wallet, Map<BlockTransactionHashIndex, WalletNode> walletUtxos, WalletNode purposeNode) {
        for(WalletNode addressNode : purposeNode.getChildren()) {
            for(BlockTransactionHashIndex utxo : addressNode.getUnspentTransactionOutputs()) {
                walletUtxos.put(utxo, addressNode);
            }
        }
    }
}
