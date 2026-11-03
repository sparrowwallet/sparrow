package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.sparrowwallet.sparrow.wallet.WalletTransactionsEntryTest.receive;
import static com.sparrowwallet.sparrow.wallet.WalletTransactionsEntryTest.receiveNode;
import static com.sparrowwallet.sparrow.wallet.WalletTransactionsEntryTest.testWallet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A label change arrives as entries built outside the tables, which relabel the matching entries held in each table's tree.
 */
public class EntryTest {
    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setup() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
        Network.set(null);
    }

    @Test
    public void changedEntriesRelabelTheirMatchesInTheTree() {
        Wallet wallet = testWallet();
        BlockTransaction labelled = receive(wallet, 0, 100000L);
        BlockTransaction unlabelled = receive(wallet, 1, 200000L);
        WalletNode labelledNode = receiveNode(wallet, 0);
        BlockTransactionHashIndex labelledOutput = labelledNode.getTransactionOutputs().iterator().next();

        WalletTransactionsEntry transactionsRoot = new WalletTransactionsEntry(wallet);
        NodeEntry addressesRoot = new NodeEntry(wallet, wallet.getNode(KeyPurpose.RECEIVE));

        labelled.setLabel("Payment");
        labelledOutput.setLabel("Payment (received)");
        labelledNode.setLabel("Payment");
        //Built as WalletForm builds them: the transaction entry has no children, unlike the one held in the transactions tree
        List<Entry> changed = List.of(new TransactionEntry(wallet, labelled, Collections.emptyMap(), Collections.emptyMap()),
                new HashIndexEntry(wallet, labelledOutput, HashIndexEntry.Type.OUTPUT, KeyPurpose.RECEIVE), new NodeEntry(wallet, labelledNode, false));

        transactionsRoot.updateLabels(changed);
        addressesRoot.updateLabels(changed);

        TransactionEntry labelledEntry = transactionEntry(transactionsRoot, labelled);
        assertEquals("Payment", labelledEntry.getLabel());
        assertEquals("Payment (received)", labelledEntry.getChildren().getFirst().getLabel());
        assertNull(transactionEntry(transactionsRoot, unlabelled).getLabel());

        NodeEntry labelledNodeEntry = nodeEntry(addressesRoot, labelledNode);
        assertEquals("Payment", labelledNodeEntry.getLabel());
        assertEquals("Payment (received)", labelledNodeEntry.getChildren().getFirst().getLabel());
        assertNull(nodeEntry(addressesRoot, receiveNode(wallet, 1)).getLabel());
    }

    @Test
    public void entriesFromAnotherWalletLeaveTheTreeUnchanged() {
        Wallet wallet = testWallet();
        BlockTransaction received = receive(wallet, 0, 100000L);
        WalletTransactionsEntry transactionsRoot = new WalletTransactionsEntry(wallet);

        Wallet otherWallet = testWallet();
        received.setLabel("Payment");
        transactionsRoot.updateLabels(List.of(new TransactionEntry(otherWallet, received, Collections.emptyMap(), Collections.emptyMap())));

        assertNull(transactionEntry(transactionsRoot, received).getLabel());
    }

    private static TransactionEntry transactionEntry(WalletTransactionsEntry root, BlockTransaction blockTransaction) {
        return root.getChildren().stream().map(TransactionEntry.class::cast).filter(entry -> entry.getBlockTransaction().equals(blockTransaction)).findFirst().orElseThrow();
    }

    private static NodeEntry nodeEntry(NodeEntry root, WalletNode node) {
        return root.getChildren().stream().map(NodeEntry.class::cast).filter(entry -> entry.getNode().equals(node)).findFirst().orElseThrow();
    }
}
