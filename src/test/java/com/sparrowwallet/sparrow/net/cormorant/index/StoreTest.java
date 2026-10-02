package com.sparrowwallet.sparrow.net.cormorant.index;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.Category;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.FeesMempoolEntry;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.ListTransaction;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.MempoolEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StoreTest {
    private static final String FIRST_TXID = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String SECOND_TXID = "0000000000000000000000000000000000000000000000000000000000000002";

    @Test
    public void testHistoryIsUnaffectedByLaterUpdates() throws InvalidAddressException {
        Store store = new Store();
        Address address = Address.fromString("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4");
        store.addAddressTransaction(address, transaction(address, FIRST_TXID, 0));
        store.addAddressTransaction(address, transaction(address, SECOND_TXID, 1));
        String scriptHash = Store.getScriptHash(address);

        //A client connection serialises the history after it is returned, while the polling thread goes on updating the store
        Iterator<TxEntry> history = store.getHistory(scriptHash).iterator();
        store.purgeTransaction(FIRST_TXID);

        List<String> served = new ArrayList<>();
        history.forEachRemaining(txEntry -> served.add(txEntry.tx_hash));
        assertEquals(List.of(FIRST_TXID, SECOND_TXID), served, "The history returned must be the one at the time it was requested");
        assertEquals(List.of(SECOND_TXID), store.getHistory(scriptHash).stream().map(txEntry -> txEntry.tx_hash).toList());
    }

    /**
     * A reorg that confirms a transaction again at the same height can place it elsewhere in the block. It is listed once, at its new position, and
     * listing it again unchanged - which the wallet does on every poll until the next block - is not an update.
     */
    @Test
    public void testTransactionMovedWithinItsBlockIsListedOnce() throws InvalidAddressException {
        Store store = new Store();
        Address address = Address.fromString("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4");
        String scriptHash = Store.getScriptHash(address);
        assertEquals(scriptHash, store.addAddressTransaction(address, transaction(address, FIRST_TXID, 3)));
        assertNull(store.addAddressTransaction(address, transaction(address, FIRST_TXID, 3)));

        assertEquals(scriptHash, store.addAddressTransaction(address, transaction(address, FIRST_TXID, 5)));
        assertEquals(List.of(FIRST_TXID), store.getHistory(scriptHash).stream().map(txEntry -> txEntry.tx_hash).toList());
        assertNull(store.addAddressTransaction(address, transaction(address, FIRST_TXID, 5)));
    }

    /**
     * The position such a transaction leaves can be taken by another to the same address, and the wallet may list either of them first.
     */
    @Test
    public void testTransactionTakingAVacatedPositionIsListed() throws InvalidAddressException {
        Address address = Address.fromString("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4");
        String scriptHash = Store.getScriptHash(address);

        Store movedFirst = new Store();
        movedFirst.addAddressTransaction(address, transaction(address, FIRST_TXID, 3));
        movedFirst.addAddressTransaction(address, transaction(address, FIRST_TXID, 5));
        assertEquals(scriptHash, movedFirst.addAddressTransaction(address, transaction(address, SECOND_TXID, 3)));
        assertEquals(List.of(SECOND_TXID, FIRST_TXID), movedFirst.getHistory(scriptHash).stream().map(txEntry -> txEntry.tx_hash).toList());

        Store movedLast = new Store();
        movedLast.addAddressTransaction(address, transaction(address, FIRST_TXID, 3));
        assertEquals(scriptHash, movedLast.addAddressTransaction(address, transaction(address, SECOND_TXID, 3)));
        movedLast.addAddressTransaction(address, transaction(address, FIRST_TXID, 5));
        assertEquals(List.of(SECOND_TXID, FIRST_TXID), movedLast.getHistory(scriptHash).stream().map(txEntry -> txEntry.tx_hash).toList());
    }

    /**
     * An unconfirmed transaction with unconfirmed parents is held at a height of its own, and the wallet goes on listing it on every poll. Neither
     * listing it nor refreshing it from the mempool is an update while nothing about it has changed.
     */
    @Test
    public void testUnconfirmedTransactionWithUnconfirmedParentsIsNotUpdatedByRelisting() throws InvalidAddressException {
        Store store = new Store();
        Address address = Address.fromString("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4");
        String scriptHash = Store.getScriptHash(address);
        ListTransaction unconfirmed = new ListTransaction(address.toString(), List.of(), Category.receive, 1.0, 0, 0.0, 0, null, 0, 0, 0, FIRST_TXID, 0, 0, List.of(), List.of());

        assertEquals(scriptHash, store.addAddressTransaction(address, unconfirmed));
        store.getMempoolEntries().put(FIRST_TXID, new MempoolEntry(100, 200, true, new FeesMempoolEntry(0.00001, 0.00002)));
        assertEquals(Set.of(scriptHash), store.updateMempoolTransactions());
        assertEquals(List.of(-1), store.getHistory(scriptHash).stream().map(txEntry -> txEntry.height).toList());
        String status = store.getStatus(scriptHash);

        assertNull(store.addAddressTransaction(address, unconfirmed));
        assertEquals(Set.of(), store.updateMempoolTransactions());
        assertEquals(status, store.getStatus(scriptHash));

        //The path that must keep working: once it confirms, the unconfirmed entry is replaced
        assertEquals(scriptHash, store.addAddressTransaction(address, transaction(address, FIRST_TXID, 0)));
        assertEquals(List.of(840000), store.getHistory(scriptHash).stream().map(txEntry -> txEntry.height).toList());
    }

    private ListTransaction transaction(Address address, String txid, int blockIndex) {
        return new ListTransaction(address.toString(), List.of(), Category.receive, 1.0, 0, 0.0, 1, "0000000000000000000000000000000000000000000000000000000000000003",
                blockIndex, 0, 840000, txid, 0, 0, List.of(), List.of());
    }
}
