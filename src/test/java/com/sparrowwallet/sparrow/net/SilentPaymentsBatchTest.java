package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.HashIndex;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.silentpayments.SilentPayment;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanAddress;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentUtils;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a batch of silent payments entries adds to a wallet. The entries held for a subscription are all processed again on every refresh, so an entry
 * that cannot be processed is passed over rather than failing the batch it is in and each one after it.
 */
public class SilentPaymentsBatchTest {
    private static final String UNKNOWN_TXID = "1100000000000000000000000000000000000000000000000000000000000000";

    @TempDir
    private static Path tempHome;

    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ServerCapability previousServerCapability;
    private final Map<String, Transaction> served = new LinkedHashMap<>();
    private final Set<String> usedScriptHashes = new HashSet<>();
    private final Map<String, String> paymentTxids = new HashMap<>();

    @BeforeAll
    public static void setUpAll() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() {
        Network.set(Network.TESTNET);
        previousElectrumServerRpc = ElectrumServer.electrumServerRpc;
        previousTransport = ElectrumServer.transport;
        previousServerCapability = ElectrumServer.serverCapability;
        ElectrumServer.transport = new TcpTransport(HostAndPort.fromParts("localhost", 1));
        ElectrumServer.serverCapability = new ServerCapability(false, false, false);
        ElectrumServer.electrumServerRpc = new SimpleElectrumServerRpc() {
            @Override
            public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
                Map<String, String> result = new LinkedHashMap<>();
                txids.forEach(txid -> result.put(txid, served.containsKey(txid) ? Utils.bytesToHex(served.get(txid).bitcoinSerialize()) : Sha256Hash.ZERO_HASH.toString()));
                return result;
            }

            @Override
            public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
                Map<String, String> result = new LinkedHashMap<>();
                pathScriptHashes.forEach((path, scriptHash) -> result.put(path, usedScriptHashes.contains(scriptHash) ? Sha256Hash.of(scriptHash.getBytes()).toString() : null));
                return result;
            }

            @Override
            public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
                Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
                pathScriptHashes.forEach((path, scriptHash) -> result.put(path, paymentTxids.containsKey(scriptHash)
                        ? new ScriptHashTx[] {new ScriptHashTx(0, paymentTxids.get(scriptHash), 0L)} : new ScriptHashTx[0]));
                return result;
            }
        };
    }

    @AfterEach
    public void tearDown() {
        ElectrumServer.electrumServerRpc = previousElectrumServerRpc;
        ElectrumServer.transport = previousTransport;
        ElectrumServer.serverCapability = previousServerCapability;
        ElectrumServer.clearPreviousServerState();
        Network.set(null);
    }

    @Test
    public void addsANodeForEachPaymentFound() throws Exception {
        Wallet wallet = silentPaymentsWallet();
        List<SilentPaymentsTx> entries = List.of(payment(wallet, 1, 10000L), payment(wallet, 2, 20000L));

        Set<WalletNode> affectedNodes = new ElectrumServer().processSilentPaymentBatch(wallet, entries);

        assertEquals(2, affectedNodes.size());
        assertEquals(30000L, walletBalance(wallet));
    }

    /**
     * A transaction can leave the mempool between being announced and being fetched. The entry naming it is skipped as a malformed one is, and
     * nothing is recorded for it, so the other entries of this batch and of those that follow are still processed.
     */
    @Test
    public void skipsAnEntryWhoseTransactionTheServerDoesNotSupply() throws Exception {
        Wallet wallet = silentPaymentsWallet();
        SilentPaymentsTx first = payment(wallet, 1, 10000L);
        SilentPaymentsTx unfetchable = new SilentPaymentsTx(0, UNKNOWN_TXID, first.tweak_key);

        assertEquals(1, new ElectrumServer().processSilentPaymentBatch(wallet, List.of(unfetchable, first)).size());
        assertFalse(wallet.getTransactions().containsKey(Sha256Hash.wrap(UNKNOWN_TXID)));

        SilentPaymentsTx second = payment(wallet, 2, 20000L);
        assertEquals(1, new ElectrumServer().processSilentPaymentBatch(wallet, List.of(unfetchable, first, second)).size());
        assertEquals(2, wallet.getNode(KeyPurpose.RECEIVE).getChildren().size());
        assertEquals(30000L, walletBalance(wallet));
    }

    /**
     * A skipped entry is asked for again on each refresh, so its payment is found once the server has the transaction.
     */
    @Test
    public void findsAPaymentOnceTheServerSuppliesItsTransaction() throws Exception {
        Wallet wallet = silentPaymentsWallet();
        SilentPaymentsTx payment = payment(wallet, 1, 10000L);
        Transaction transaction = served.remove(payment.tx_hash);

        assertTrue(new ElectrumServer().processSilentPaymentBatch(wallet, List.of(payment)).isEmpty());
        assertEquals(0L, walletBalance(wallet));

        served.put(payment.tx_hash, transaction);
        assertEquals(1, new ElectrumServer().processSilentPaymentBatch(wallet, List.of(payment)).size());
        assertEquals(10000L, walletBalance(wallet));
    }

    /**
     * A failed script hash fetch leaves the unfetchable placeholder in the wallet's transactions. It is fetched again rather than taken as the transaction.
     */
    @Test
    public void fetchesAgainATransactionTheWalletHoldsAsUnfetchable() throws Exception {
        Wallet wallet = silentPaymentsWallet();
        SilentPaymentsTx payment = payment(wallet, 1, 10000L);
        wallet.updateTransactions(Map.of(Sha256Hash.wrap(payment.tx_hash), ElectrumServer.UNFETCHABLE_BLOCK_TRANSACTION));

        assertEquals(1, new ElectrumServer().processSilentPaymentBatch(wallet, List.of(payment)).size());
        assertEquals(10000L, walletBalance(wallet));
    }

    /**
     * Silent payments nodes are not derived, so the wallet may hold a node without those below it. Fetching its history does not try to fill to it.
     */
    @Test
    public void fetchesTheHistoryOfNodesWithoutContiguousIndexes() {
        Wallet wallet = silentPaymentsWallet();
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).addSilentPaymentChild(wallet, 1, Utils.hexToBytes("c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"));
        usedScriptHashes.add(ElectrumServer.getScriptHash(node));

        Map<WalletNode, Set<BlockTransactionHash>> history = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> new ElectrumServer().getHistory(wallet));
        assertTrue(history.containsKey(node));
        assertEquals(1, wallet.getNode(KeyPurpose.RECEIVE).getChildren().size());
    }

    /**
     * A deterministic wallet still derives and fetches nodes to the gap limit past the highest used one.
     */
    @Test
    public void fetchesTheHistoryOfDeterministicNodesToTheGapLimit() {
        Wallet wallet = deterministicWallet();
        WalletNode receiveNode = wallet.getNode(KeyPurpose.RECEIVE);
        receiveNode.fillToIndex(wallet, 1);
        WalletNode usedNode = receiveNode.getChildren().stream().filter(node -> node.getIndex() == 1).findFirst().orElseThrow();
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.of(new byte[] {1}), 0, new Script(new byte[0]));
        transaction.addOutput(10000L, usedNode.getAddress());
        served.put(transaction.getTxId().toString(), transaction);
        usedScriptHashes.add(ElectrumServer.getScriptHash(usedNode));
        paymentTxids.put(ElectrumServer.getScriptHash(usedNode), transaction.getTxId().toString());

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> new ElectrumServer().getHistory(wallet));
        assertEquals(2 + wallet.getGapLimit(), receiveNode.getChildren().size());
    }

    /**
     * Builds a payment to the wallet's silent payment address, which the server then holds in its mempool, returning the entry a subscription reports for it.
     */
    private SilentPaymentsTx payment(Wallet wallet, int sender, long amount) throws Exception {
        ECKey senderKey = ECKey.fromPrivate(BigInteger.valueOf(1000 + sender));
        HashIndex outpoint = new HashIndex(Sha256Hash.of(new byte[] {(byte)sender}), 0);
        SilentPayment silentPayment = new SilentPayment(wallet.getSilentPaymentScanAddress().getSilentPaymentAddress(), null, amount, false);
        SilentPaymentUtils.computeOutputAddresses(List.of(silentPayment), senderKey, Set.of(outpoint));

        Transaction transaction = new Transaction();
        transaction.addInput(outpoint.getHash(), outpoint.getIndex(), new Script(new byte[0]));
        transaction.addOutput(amount, silentPayment.getAddress());
        served.put(transaction.getTxId().toString(), transaction);

        ECKey senderPubKey = ECKey.fromPublicOnly(senderKey);
        BigInteger inputHash = SilentPaymentUtils.getInputHash(Set.of(outpoint), senderPubKey);
        return new SilentPaymentsTx(0, transaction.getTxId().toString(), Utils.bytesToHex(senderPubKey.multiply(inputHash, true).getPubKey(true)));
    }

    private static long walletBalance(Wallet wallet) {
        return wallet.getWalletUtxos().keySet().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
    }

    private static Wallet silentPaymentsWallet() {
        ECKey scanKey = ECKey.fromPrivate(Utils.hexToBytes("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
        ECKey spendKey = ECKey.fromPrivate(Utils.hexToBytes("b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3"));

        Wallet wallet = new Wallet("silentpayments");
        wallet.setPolicyType(PolicyType.SINGLE_SP);
        wallet.setScriptType(ScriptType.P2TR);
        Keystore keystore = new Keystore();
        keystore.setSilentPaymentScanAddress(new SilentPaymentScanAddress(scanKey, ECKey.fromPublicOnly(spendKey)));
        keystore.setKeyDerivation(new KeyDerivation("deadbeef", "m/352'/1'/0'"));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_SP, ScriptType.P2TR, wallet.getKeystores(), 1));
        return wallet;
    }

    private static Wallet deterministicWallet() {
        Wallet wallet = new Wallet("deterministic");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        Keystore keystore = new Keystore();
        keystore.setKeyDerivation(new KeyDerivation("00000000", "m/84'/1'/0'"));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor("tpubD6NzVbkrYhZ4XzL5Dhayo67Gorv1YMS7j8pRUvVMd5odC2LBPLAygka9p7748JtSq82FNGPppFEz5xxZUdasBRCqJqXvUHq6xpnsMcYJzeh"));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), 1));
        return wallet;
    }
}
