package com.sparrowwallet.sparrow.net.cormorant.index;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.Category;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.ListTransaction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private ListTransaction transaction(Address address, String txid, int blockIndex) {
        return new ListTransaction(address.toString(), List.of(), Category.receive, 1.0, 0, 0.0, 1, "0000000000000000000000000000000000000000000000000000000000000003",
                blockIndex, 0, 840000, txid, 0, 0, List.of(), List.of());
    }
}
