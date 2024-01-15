package com.sparrowwallet.sparrow.net.cormorant.index;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.Category;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.ListTransaction;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.MempoolEntry;
import com.sparrowwallet.drongo.protocol.HashIndex;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.Utils;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class Store {
    private final Map<String, Set<TxEntry>> scriptHashEntries = new HashMap<>();
    private final Map<HashIndex, Address> fundingAddresses = new HashMap<>();
    private final Map<String, Set<HashIndex>> spentOutputs = new HashMap<>();
    private final Map<Integer, String> blockHeightHashes = new HashMap<>();
    private final Map<String, MempoolEntry> mempoolEntries = new HashMap<>();

    public String addAddressTransaction(Address address, ListTransaction listTransaction) {
        if(listTransaction.category() == Category.receive || listTransaction.category() == Category.immature || listTransaction.category() == Category.generate) {
            fundingAddresses.put(new HashIndex(Sha256Hash.wrap(listTransaction.txid()), listTransaction.vout()), address);
        }

        blockHeightHashes.put(listTransaction.blockheight(), listTransaction.blockhash());

        String scriptHash = getScriptHash(address);
        Set<TxEntry> entries = scriptHashEntries.computeIfAbsent(scriptHash, k -> new TreeSet<>());
        TxEntry txEntry;
        String txid = listTransaction.txid();

        if(listTransaction.confirmations() == 0) {
            if(!mempoolEntries.containsKey(txid)) {
                mempoolEntries.put(txid, null);
            }
            entries.removeIf(txe -> txe.height > 0 && txe.tx_hash.equals(listTransaction.txid()));
            txEntry = new TxEntry(0, 0, listTransaction.txid());
        } else {
            mempoolEntries.remove(txid);
            entries.removeIf(txe -> txe.height != listTransaction.blockheight() && txe.tx_hash.equals(listTransaction.txid()));
            txEntry = new TxEntry(listTransaction.blockheight(), listTransaction.blockindex(), listTransaction.txid());
        }

        if(entries.add(txEntry)) {
            return scriptHash;
        }

        return null;
    }

    public Set<String> updateMempoolTransactions() {
        Set<String> updatedScriptHashes = new HashSet<>();

        for(Map.Entry<String, Set<TxEntry>> scriptHashEntry : scriptHashEntries.entrySet()) {
            Set<TxEntry> txEntries = scriptHashEntry.getValue();
            Set<TxEntry> oldEntries = new HashSet<>();
            Set<TxEntry> newEntries = new HashSet<>();
            for(TxEntry txEntry : txEntries) {
                if(txEntry.height <= 0) {
                    MempoolEntry mempoolEntry = mempoolEntries.get(txEntry.tx_hash);
                    TxEntry newEntry = (mempoolEntry == null ? null : mempoolEntry.getTxEntry(txEntry.tx_hash));
                    if(!txEntry.equals(newEntry)) {
                        oldEntries.add(txEntry);
                        if(newEntry != null) {
                            newEntries.add(newEntry);
                        }
                    }
                }
            }

            boolean removed = txEntries.removeAll(oldEntries);
            boolean added = txEntries.addAll(newEntries);

            if(added || removed) {
                updatedScriptHashes.add(scriptHashEntry.getKey());
            }
        }

        return updatedScriptHashes;
    }

    public Set<String> purgeTransaction(String txid) {
        Set<String> updatedScriptHashes = new HashSet<>();

        for(Map.Entry<String, Set<TxEntry>> scriptHashEntry : scriptHashEntries.entrySet()) {
            Set<TxEntry> txEntries = scriptHashEntry.getValue();
            if(txEntries.removeIf(txEntry -> txEntry.tx_hash.equals(txid))) {
                updatedScriptHashes.add(scriptHashEntry.getKey());
            }
        }

        Sha256Hash txHash = Sha256Hash.wrap(txid);
        fundingAddresses.keySet().removeIf(hashIndex -> hashIndex.getHash().equals(txHash));
        spentOutputs.remove(txid);
        mempoolEntries.remove(txid);

        return updatedScriptHashes;
    }

    public String getStatus(String scriptHash) {
        Set<TxEntry> entries = scriptHashEntries.get(scriptHash);
        if(entries == null || entries.isEmpty()) {
            return null;
        }

        StringBuilder scriptHashStatus = new StringBuilder();
        for(TxEntry entry : entries) {
            scriptHashStatus.append(entry.tx_hash).append(":").append(entry.height).append(":");
        }

        return Utils.bytesToHex(Sha256Hash.hash(scriptHashStatus.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public Address getFundingAddress(HashIndex spentOutput) {
        return fundingAddresses.get(spentOutput);
    }

    public Map<String, Set<HashIndex>> getSpentOutputs() {
        return spentOutputs;
    }

    public Map<String, MempoolEntry> getMempoolEntries() {
        return mempoolEntries;
    }

    public Set<TxEntry> getHistory(String scriptHash) {
        Set<TxEntry> entries = scriptHashEntries.get(scriptHash);
        if(entries == null) {
            return Collections.emptySet();
        }

        return entries;
    }

    public String getBlockHash(int height) {
        return blockHeightHashes.get(height);
    }

    public static String getScriptHash(Address address) {
        byte[] hash = Sha256Hash.hash(address.getOutputScript().getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }
}
