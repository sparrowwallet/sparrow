package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanAddress;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletHistoryFailedEvent;
import com.sparrowwallet.sparrow.net.ServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which nodes a wallet update reports as changed, and so which rows the persistence layer rewrites. The case covered here is the one the ordinary
 * comparison cannot see: a transaction re-proven against a different block at an unchanged height, whose block hash and date are stale on disk until
 * the nodes holding it are written again.
 */
public class WalletFormTest {
    private static final String TEST_XPUB = "xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj";

    private static final Sha256Hash TXID = Sha256Hash.wrap("f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16");
    private static final Sha256Hash ORPHANED_BLOCK = Sha256Hash.wrap("00000000000000000001a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6");
    private static final Sha256Hash REPLACING_BLOCK = Sha256Hash.wrap("00000000000000000002b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f607");

    private static final int HEIGHT = 800000;

    @BeforeEach
    public void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    public void tearDown() {
        Network.set(null);
    }

    /**
     * The same-height reorg: the height, the outputs and the wallet's own view of them are all unchanged, and only the block the transaction was
     * proven against differs. The node has to be reported so that its outputs and its transaction row are written with the replacing block's hash and
     * timestamp, which are otherwise corrected in memory and lost on restart.
     */
    @Test
    public void reportsTheNodeHoldingATransactionProvenAgainstAnotherBlock() {
        Wallet wallet = testWallet();
        WalletNode node = receiveNode(wallet, 0);
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 10000));
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));

        Wallet previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000600000L), REPLACING_BLOCK)));

        List<WalletNode> changedNodes = new ArrayList<>();
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);

        assertEquals(List.of(node), changedNodes);
    }

    /**
     * A node whose output was spent in the replaced block is the same case, since the spend carries the stale height's date too.
     */
    @Test
    public void reportsTheNodeWhoseSpendWasProvenAgainstAnotherBlock() {
        Wallet wallet = testWallet();
        WalletNode node = receiveNode(wallet, 0);
        BlockTransactionHashIndex output = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 700000, new Date(1500000000000L), 0L, 0, 10000);
        output.setSpentBy(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 10000));
        node.getTransactionOutputs().add(output);
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));

        Wallet previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000600000L), REPLACING_BLOCK)));

        List<WalletNode> changedNodes = new ArrayList<>();
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);

        assertEquals(List.of(node), changedNodes);
    }

    /**
     * The non-regression that matters most: a wallet whose transactions predate the feature carries no block hash at all, and an ordinary pass must
     * report nothing here, or every node would be rewritten on every update.
     */
    @Test
    public void reportsNothingWhereNoBlockWasProvenAgainstAnother() {
        Wallet wallet = testWallet();
        WalletNode node = receiveNode(wallet, 0);
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 10000));

        //No block hash at all, as everything written before this feature has
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), null)));
        Wallet previousWallet = wallet.copy();
        List<WalletNode> changedNodes = new ArrayList<>();
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);
        assertTrue(changedNodes.isEmpty());

        //And a pass that rewrites the transaction without changing the block it was proven against
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), null)));
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);
        assertTrue(changedNodes.isEmpty());

        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));
        Wallet provenWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));
        WalletForm.addReprovenNodes(wallet, provenWallet, changedNodes);
        assertTrue(changedNodes.isEmpty());
    }

    /**
     * A block hash follows the height, so it changes on every ordinary confirmation, demotion and unfetchable transaction too. In each of those the
     * node has an output at a new height and the ordinary comparison has already reported it, so this must not walk the wallet again to reach it.
     */
    @Test
    public void reportsNothingWhereTheHeightChangedWithTheBlock() {
        Wallet wallet = testWallet();
        WalletNode node = receiveNode(wallet, 0);
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(TXID, 0, null, 0L, 0, 10000));
        wallet.updateTransactions(Map.of(TXID, new BlockTransaction(TXID, 0, null, 0L, null, null)));

        //Confirmed: a height and a block hash where there was neither
        Wallet previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));
        List<WalletNode> changedNodes = new ArrayList<>();
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);
        assertTrue(changedNodes.isEmpty());

        //Demoted: the block hash goes away with the height
        previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, new BlockTransaction(TXID, 0, null, 0L, null, null)));
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);
        assertTrue(changedNodes.isEmpty());
    }

    /**
     * A node already reported by the ordinary comparison is not reported twice.
     */
    @Test
    public void doesNotReportANodeTwice() {
        Wallet wallet = testWallet();
        WalletNode node = receiveNode(wallet, 0);
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 10000));
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));

        Wallet previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000600000L), REPLACING_BLOCK)));

        List<WalletNode> changedNodes = new ArrayList<>(List.of(node));
        WalletForm.addReprovenNodes(wallet, previousWallet, changedNodes);

        assertEquals(List.of(node), changedNodes);
    }

    /**
     * A silent payments scan adds the payments it finds to the wallet before fetching their history. Where that fetch fails, the nodes are reported
     * against the copy taken before the refresh so that they are saved, since the next refresh copies a wallet already holding them. The stored block
     * height, where the next scan starts, is left unchanged.
     */
    @Test
    public void reportsTheNodesAddedByARefreshThatFailed() {
        Wallet wallet = silentPaymentsWallet();
        wallet.setStoredBlockHeight(HEIGHT - 1000);
        WalletForm walletForm = new WalletForm(null, wallet);

        Wallet previousWallet = wallet.copy();
        wallet.updateTransactions(Map.of(TXID, blockTransaction(new Date(1600000000000L), ORPHANED_BLOCK)));
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).addSilentPaymentChild(wallet, 0, Utils.hexToBytes("c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"));
        node.updateTransactionOutputs(wallet, Set.of(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 50000)));

        HistoryFailedListener listener = new HistoryFailedListener();
        EventManager.get().register(listener);
        try {
            assertTrue(reportsChanges(() -> walletForm.handleHistoryFailed(previousWallet, new ServerException("Failed to subscribe"))));
            assertEquals(1, listener.events);
            assertEquals(HEIGHT - 1000, wallet.getStoredBlockHeight());

            //A refresh that changed nothing before failing reports the failure alone
            Wallet unchangedWallet = wallet.copy();
            assertFalse(reportsChanges(() -> walletForm.handleHistoryFailed(unchangedWallet, new ServerException("Failed to subscribe"))));
            assertEquals(2, listener.events);
        } finally {
            EventManager.get().unregister(listener);
        }
    }

    /**
     * A labelled address with no history has already been given out, so the desktop and terminal receive views both pass over it rather than handing it to
     * a second payer.
     */
    @Test
    public void freshEntrySkipsLabelledAddresses() {
        Wallet wallet = testWallet();
        receiveNode(wallet, 0).setLabel("Invoice 1");
        receiveNode(wallet, 1).setLabel("Invoice 2");
        WalletForm walletForm = new WalletForm(null, wallet);

        NodeEntry freshEntry = walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, null);
        assertEquals(2, freshEntry.getNode().getIndex());
        assertEquals(3, walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, freshEntry).getNode().getIndex());

        //An empty label does not allocate
        receiveNode(wallet, 3).setLabel("");
        assertEquals(3, walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, freshEntry).getNode().getIndex());
    }

    /**
     * Stepping past the gap limit widens it, so that a recovery with the persisted limit still reaches the address that was issued. An address within the
     * limit leaves it unchanged.
     */
    @Test
    public void issuingAnAddressPastTheGapLimitWidensIt() {
        Wallet wallet = testWallet();
        WalletForm walletForm = new WalletForm(null, wallet) {
            @Override
            public String getWalletId() {
                return "test";
            }
        };

        int gapLimit = wallet.getGapLimit();
        walletForm.ensureSufficientGapLimit(walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, entryAt(walletForm, gapLimit - 2)));
        assertEquals(gapLimit, wallet.getGapLimit());

        walletForm.ensureSufficientGapLimit(walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, entryAt(walletForm, gapLimit + 4)));
        assertEquals(gapLimit + 6, wallet.getGapLimit());

        //Measured from the highest used address rather than from the first: index 29 is past the limit from nothing used, and within it from index 10
        receiveNode(wallet, 10).getTransactionOutputs().add(new BlockTransactionHashIndex(TXID, HEIGHT, new Date(1600000000000L), 0L, 0, 10000));
        walletForm.ensureSufficientGapLimit(walletForm.getFreshNodeEntry(KeyPurpose.RECEIVE, entryAt(walletForm, 28)));
        assertEquals(gapLimit + 6, wallet.getGapLimit());
    }

    /**
     * Returns whether the wallet form reported changed nodes. They are posted through Platform.runLater, and no test here starts the JavaFX toolkit,
     * so reaching that call is observable as its refusal.
     */
    private static boolean reportsChanges(Runnable refreshHandler) {
        try {
            refreshHandler.run();
            return false;
        } catch(IllegalStateException e) {
            assertEquals("Toolkit not initialized", e.getMessage());
            return true;
        }
    }

    private static class HistoryFailedListener {
        private int events;

        @Subscribe
        public void walletHistoryFailed(WalletHistoryFailedEvent event) {
            events++;
        }
    }

    private static NodeEntry entryAt(WalletForm walletForm, int index) {
        Wallet wallet = walletForm.getWallet();
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(wallet, index);
        return new NodeEntry(wallet, receiveNode(wallet, index));
    }

    private static BlockTransaction blockTransaction(Date date, Sha256Hash blockHash) {
        return new BlockTransaction(TXID, HEIGHT, date, 0L, null, blockHash);
    }

    private static WalletNode receiveNode(Wallet wallet, int index) {
        return wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getIndex() == index).findFirst().orElseThrow();
    }

    private static Wallet silentPaymentsWallet() {
        ECKey scanKey = ECKey.fromPrivate(Utils.hexToBytes("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
        ECKey spendKey = ECKey.fromPrivate(Utils.hexToBytes("b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3"));

        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE_SP);
        wallet.setScriptType(ScriptType.P2TR);
        Keystore keystore = new Keystore();
        keystore.setSilentPaymentScanAddress(new SilentPaymentScanAddress(scanKey, ECKey.fromPublicOnly(spendKey)));
        keystore.setKeyDerivation(new KeyDerivation("00000000", "m/352'/0'/0'"));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_SP, ScriptType.P2TR, wallet.getKeystores(), 1));

        return wallet;
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

        return wallet;
    }
}
