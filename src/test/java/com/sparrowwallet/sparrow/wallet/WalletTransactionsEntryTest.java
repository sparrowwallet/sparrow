package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.WalletBlockHeightChangedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Which transaction entries follow the chain tip. Only an entry held in a wallet form's transactions model is shown, while a refresh builds an entry for every
 * transaction and keeps only those it does not already hold, so an entry that registered itself on construction left a subscriber behind on every refresh.
 */
public class WalletTransactionsEntryTest {
    private static final String TEST_XPUB = "xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj";
    private static final Sha256Hash FUNDING_TXID = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");
    private static final int HEIGHT = 850000;

    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setup() {
        //Isolate Config.get(), which the balance calculation reads, from the developer's config
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        Network.set(Network.MAINNET);
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
        Network.set(null);
    }

    @Test
    public void entryHeldInTheModelFollowsTheTip() {
        Wallet wallet = testWallet();
        BlockTransaction received = receive(wallet, 0, 100000L);
        WalletTransactionsEntry walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        walletTransactionsEntry.registerForConfirmations();

        try {
            TransactionEntry held = entryFor(walletTransactionsEntry, received);
            assertEquals(1, held.getConfirmations());
            advanceTip(wallet, HEIGHT + 1);
            assertEquals(2, held.getConfirmations());
        } finally {
            walletTransactionsEntry.unregisterForConfirmations();
        }
    }

    /**
     * The entries a refresh discards, those carrying a label change, and the balance chart's snapshot in the wallet summary are all built this way.
     */
    @Test
    public void entryBuiltOutsideTheModelDoesNotFollowTheTip() {
        Wallet wallet = testWallet();
        BlockTransaction received = receive(wallet, 0, 100000L);
        WalletTransactionsEntry walletTransactionsEntry = new WalletTransactionsEntry(wallet);

        TransactionEntry unheld = entryFor(walletTransactionsEntry, received);
        advanceTip(wallet, HEIGHT + 1);
        assertEquals(1, unheld.getConfirmations());
    }

    @Test
    public void entryRemovedFromTheModelStopsFollowingTheTip() {
        Wallet wallet = testWallet();
        BlockTransaction kept = receive(wallet, 0, 100000L);
        BlockTransaction dropped = receive(wallet, 1, 200000L);
        WalletTransactionsEntry walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        walletTransactionsEntry.registerForConfirmations();

        try {
            TransactionEntry keptEntry = entryFor(walletTransactionsEntry, kept);
            TransactionEntry droppedEntry = entryFor(walletTransactionsEntry, dropped);

            receiveNode(wallet, 1).getTransactionOutputs().clear();
            walletTransactionsEntry.updateTransactions();
            assertFalse(walletTransactionsEntry.getChildren().contains(droppedEntry));

            advanceTip(wallet, HEIGHT + 1);
            assertEquals(1, droppedEntry.getConfirmations());
            assertEquals(2, keptEntry.getConfirmations());
        } finally {
            walletTransactionsEntry.unregisterForConfirmations();
        }
    }

    @Test
    public void entryAddedByARefreshFollowsTheTip() {
        Wallet wallet = testWallet();
        BlockTransaction existing = receive(wallet, 0, 100000L);
        WalletTransactionsEntry walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        walletTransactionsEntry.registerForConfirmations();

        try {
            TransactionEntry existingEntry = entryFor(walletTransactionsEntry, existing);
            BlockTransaction added = receive(wallet, 1, 200000L);
            walletTransactionsEntry.updateTransactions();
            TransactionEntry addedEntry = entryFor(walletTransactionsEntry, added);
            assertEquals(existingEntry, entryFor(walletTransactionsEntry, existing));

            advanceTip(wallet, HEIGHT + 1);
            assertEquals(2, addedEntry.getConfirmations());
            assertEquals(2, entryFor(walletTransactionsEntry, existing).getConfirmations());
        } finally {
            walletTransactionsEntry.unregisterForConfirmations();
        }
    }

    private static void advanceTip(Wallet wallet, int height) {
        wallet.setStoredBlockHeight(height);
        EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, height));
    }

    private static TransactionEntry entryFor(WalletTransactionsEntry walletTransactionsEntry, BlockTransaction blockTransaction) {
        return walletTransactionsEntry.getChildren().stream().map(TransactionEntry.class::cast)
                .filter(entry -> entry.getBlockTransaction().getHash().equals(blockTransaction.getHash())).findFirst().orElseThrow();
    }

    private static BlockTransaction receive(Wallet wallet, int index, long value) {
        WalletNode node = receiveNode(wallet, index);
        Transaction transaction = new Transaction();
        transaction.addInput(FUNDING_TXID, index, new Script(new byte[0]));
        transaction.addOutput(value, node.getAddress());
        Date date = new Date(1700000000000L);
        BlockTransaction blockTransaction = new BlockTransaction(transaction.getTxId(), HEIGHT, date, null, transaction);
        wallet.updateTransactions(Map.of(transaction.getTxId(), blockTransaction));
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(transaction.getTxId(), HEIGHT, date, null, 0, value));

        return blockTransaction;
    }

    private static WalletNode receiveNode(Wallet wallet, int index) {
        return wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getIndex() == index).findFirst().orElseThrow();
    }

    private static Wallet testWallet() {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        Keystore keystore = new Keystore();
        keystore.setKeyDerivation(new KeyDerivation("00000000", "m/84'/0'/0'"));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(TEST_XPUB));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), 1));
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(wallet, 1);
        wallet.setStoredBlockHeight(HEIGHT);

        return wallet;
    }
}
