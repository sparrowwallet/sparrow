package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static BlockTransaction blockTransaction(Date date, Sha256Hash blockHash) {
        return new BlockTransaction(TXID, HEIGHT, date, 0L, null, blockHash);
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

        return wallet;
    }
}
