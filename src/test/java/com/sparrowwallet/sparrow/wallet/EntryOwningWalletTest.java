package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

public class EntryOwningWalletTest {
    private static Wallet nestedWallet(Wallet masterWallet) {
        Wallet nested = new Wallet("PayNym");
        Keystore keystore = new Keystore();
        keystore.setSource(KeystoreSource.SW_PAYMENT_CODE);
        nested.getKeystores().add(keystore);
        nested.setMasterWallet(masterWallet);
        masterWallet.getChildWallets().add(nested);
        Assertions.assertTrue(nested.isNested());
        return nested;
    }

    @Test
    public void nestedOutputIsShownUnderTheMasterAndOwnedByTheNestedWallet() {
        Wallet master = new Wallet("Master");
        Wallet nested = nestedWallet(master);

        BlockTransactionHashIndex output = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 100, null, null, 0, 1000);
        HashIndexEntry entry = new HashIndexEntry(nested, output, HashIndexEntry.Type.OUTPUT, KeyPurpose.RECEIVE);
        Assertions.assertSame(master, entry.getWallet());
        Assertions.assertSame(nested, entry.getOwningWallet());
    }

    @Test
    public void sharedTransactionChildrenAreOwnedByTheWalletStoringEachOutput() {
        Wallet master = new Wallet("Master");
        master.setStoredBlockHeight(200);
        Wallet nested = nestedWallet(master);

        BlockTransactionHashIndex masterOutput = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 100, null, null, 0, 1000);
        BlockTransactionHashIndex nestedOutput = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 100, null, null, 1, 2000);
        Map<BlockTransactionHashIndex, KeyPurpose> incoming = new TreeMap<>(Map.of(masterOutput, KeyPurpose.RECEIVE, nestedOutput, KeyPurpose.RECEIVE));
        BlockTransaction blockTransaction = new BlockTransaction(Sha256Hash.ZERO_HASH, 100, null, null, new Transaction());

        TransactionEntry transactionEntry = new TransactionEntry(master, blockTransaction, incoming, new TreeMap<>(), Map.of(nestedOutput, nested));
        Assertions.assertSame(master, transactionEntry.getOwningWallet());
        for(Entry child : transactionEntry.getChildren()) {
            HashIndexEntry hashIndexEntry = (HashIndexEntry)child;
            Assertions.assertSame(master, hashIndexEntry.getWallet());
            Assertions.assertSame(hashIndexEntry.getHashIndex() == nestedOutput ? nested : master, hashIndexEntry.getOwningWallet());
        }
    }
}
