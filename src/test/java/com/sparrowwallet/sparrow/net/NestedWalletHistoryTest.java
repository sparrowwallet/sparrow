package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The history of a wallet with a nested BIP47 wallet, refreshed address by address as the server notifies them. Each of the two wallets holds the
 * transactions its own addresses refer to and is fetched for its own addresses only, in whichever order an unconfirmed transaction reaches them.
 */
public class NestedWalletHistoryTest {
    private static final long PAYMENT = 10_000L;

    private static final long FEE = 200L;

    @TempDir
    private static Path tempHome;

    private ServerType previousServerType;
    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ServerCapability previousServerCapability;
    private MempoolServer server;
    private int coins;

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
        previousServerType = Config.get().getServerType();
        Config.get().setServerType(ServerType.ELECTRUM_SERVER);
        previousElectrumServerRpc = ElectrumServer.electrumServerRpc;
        previousTransport = ElectrumServer.transport;
        previousServerCapability = ElectrumServer.serverCapability;
        server = new MempoolServer();
        ElectrumServer.electrumServerRpc = server;
        ElectrumServer.transport = new UnusedTransport();
        ElectrumServer.serverCapability = null;
        ElectrumServer.clearPreviousServerState();
        ElectrumServer.getSubscribedScriptHashes().clear();
    }

    @AfterEach
    public void tearDown() {
        Config.get().setServerType(previousServerType);
        ElectrumServer.electrumServerRpc = previousElectrumServerRpc;
        ElectrumServer.transport = previousTransport;
        ElectrumServer.serverCapability = previousServerCapability;
        ElectrumServer.clearPreviousServerState();
        ElectrumServer.getSubscribedScriptHashes().clear();
        Network.set(null);
    }

    @Test
    public void refreshesOwnAddressWhileNestedWalletHoldsUnconfirmedOutput() throws Exception {
        Wallet wallet = softwareWallet(1);
        Wallet nestedWallet = nestedWallet(wallet);
        WalletNode ownNode = receiveNode(wallet, 3);
        WalletNode nestedNode = receiveNode(nestedWallet, 0);
        refresh(wallet, null);

        Transaction first = server.accept(payment(nestedNode));
        refresh(wallet, Set.of(nestedNode));
        assertEquals(Set.of(), wallet.getTransactions().keySet());
        assertEquals(Set.of(first.getTxId()), nestedWallet.getTransactions().keySet());

        //The nested wallet's address now holds an unconfirmed output, and has a new status, as one of the wallet's own addresses is refreshed with it
        Transaction second = server.accept(payment(ownNode, nestedNode));
        refresh(wallet, Set.of(ownNode, nestedNode));

        assertEquals(Set.of(second.getTxId()), wallet.getTransactions().keySet());
        assertEquals(Set.of(first.getTxId(), second.getTxId()), nestedWallet.getTransactions().keySet());
        assertEquals(Set.of(second.getTxId()), outputs(ownNode));
        assertEquals(Set.of(first.getTxId(), second.getTxId()), outputs(nestedNode));

        //A later payment to another of the wallet's own addresses finds the wallet as it was left
        WalletNode otherNode = receiveNode(wallet, 4);
        Transaction third = server.accept(payment(otherNode));
        refresh(wallet, Set.of(otherNode));

        assertEquals(Set.of(second.getTxId(), third.getTxId()), wallet.getTransactions().keySet());
        assertEquals(Set.of(third.getTxId()), outputs(otherNode));
        assertEquals(4 * PAYMENT, wallet.getWalletUtxos().keySet().stream().mapToLong(BlockTransactionHashIndex::getValue).sum());
    }

    @Test
    public void holdsOwnEntryForTransactionNestedWalletFetchedFirst() throws Exception {
        Wallet wallet = softwareWallet(1);
        Wallet nestedWallet = nestedWallet(wallet);
        WalletNode ownNode = receiveNode(wallet, 3);
        WalletNode nestedNode = receiveNode(nestedWallet, 0);
        refresh(wallet, null);

        //One transaction pays both wallets, and the nested wallet's address is the first to be refreshed
        Transaction transaction = server.accept(payment(ownNode, nestedNode));
        refresh(wallet, Set.of(nestedNode));
        assertEquals(Set.of(), wallet.getTransactions().keySet());
        assertEquals(Set.of(transaction.getTxId()), nestedWallet.getTransactions().keySet());

        refresh(wallet, Set.of(ownNode));

        assertEquals(Set.of(transaction.getTxId()), wallet.getTransactions().keySet());
        assertEquals(FEE, wallet.getTransactions().get(transaction.getTxId()).getFee());
        assertEquals(Set.of(transaction.getTxId()), outputs(ownNode));
        assertEquals(Set.of(transaction.getTxId()), outputs(nestedNode));
        //The wallet's entry is built from the transaction the nested wallet holds, which is not requested again
        assertEquals(List.of(transaction.getTxId().toString()), server.requested);
    }

    @Test
    public void holdsOwnEntryOnFullRefreshForTransactionOnlyNestedWalletHolds() throws Exception {
        Wallet wallet = softwareWallet(1);
        Wallet nestedWallet = nestedWallet(wallet);
        WalletNode ownNode = receiveNode(wallet, 3);
        WalletNode nestedNode = receiveNode(nestedWallet, 0);
        refresh(wallet, null);

        Transaction transaction = server.accept(payment(ownNode, nestedNode));
        refresh(wallet, Set.of(nestedNode));

        //Reconnecting fetches every address of the wallet and then of its nested wallet, knowing only what the two wallets hold
        ElectrumServer.clearPreviousServerState();
        ElectrumServer.getSubscribedScriptHashes().clear();
        refresh(wallet, null);

        assertEquals(Set.of(transaction.getTxId()), wallet.getTransactions().keySet());
        assertEquals(Set.of(transaction.getTxId()), nestedWallet.getTransactions().keySet());
        assertEquals(Set.of(transaction.getTxId()), outputs(ownNode));
        assertEquals(Set.of(transaction.getTxId()), outputs(nestedNode));
    }

    @Test
    public void revisitsOwnAddressHoldingUnconfirmedOutput() throws Exception {
        Wallet wallet = softwareWallet(1);
        nestedWallet(wallet);
        WalletNode firstNode = receiveNode(wallet, 3);
        WalletNode secondNode = receiveNode(wallet, 4);
        refresh(wallet, null);

        Transaction original = server.accept(payment(firstNode));
        refresh(wallet, Set.of(firstNode));
        assertEquals(Set.of(original.getTxId()), outputs(firstNode));

        //The payment is replaced in the mempool, and only another address is refreshed
        Transaction replacement = server.replace(original, payment(firstNode));
        Transaction other = server.accept(payment(secondNode));
        refresh(wallet, Set.of(secondNode));

        assertEquals(Set.of(replacement.getTxId()), outputs(firstNode));
        assertEquals(Set.of(other.getTxId()), outputs(secondNode));
    }

    private static void refresh(Wallet wallet, Set<WalletNode> nodes) throws ServerException {
        assertTrue(new ElectrumServer().fetchAndCalculateHistory(wallet, null, nodes));
    }

    private static Set<Sha256Hash> outputs(WalletNode node) {
        return node.getTransactionOutputs().stream().map(BlockTransactionHashIndex::getHash).collect(Collectors.toSet());
    }

    /** Pays each of the given addresses from a coin no wallet here holds */
    private Transaction payment(WalletNode... nodes) {
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.wrap(String.format("%064x", 0xc0 + coins++)), 0, new Script(new byte[0]));
        for(WalletNode node : nodes) {
            transaction.addOutput(PAYMENT, node.getAddress());
        }

        return transaction;
    }

    private static WalletNode receiveNode(Wallet wallet, int index) {
        return wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getIndex() == index).findFirst().orElseThrow();
    }

    /** The wallet BIP47 nests in the given wallet for payments from another wallet's payment code */
    private static Wallet nestedWallet(Wallet wallet) throws MnemonicException {
        return wallet.addChildWallet(softwareWallet(2).getPaymentCode(), ScriptType.P2WPKH, "payer");
    }

    private static Wallet softwareWallet(int seed) throws MnemonicException {
        Wallet wallet = new Wallet("test" + seed);
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        DeterministicSeed deterministicSeed = new DeterministicSeed(Utils.hexToBytes(String.format("%032x", seed)), "", 0);
        Keystore keystore = Keystore.fromSeed(deterministicSeed, PolicyType.SINGLE_HD, ScriptType.P2WPKH.getDefaultDerivation());
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));

        return wallet;
    }

    /**
     * A server that reports every transaction in its mempool, unconfirmed and in order of arrival, to each script hash the transaction pays.
     */
    private static class MempoolServer extends SimpleElectrumServerRpc {
        private final List<Transaction> mempool = new ArrayList<>();
        private final List<String> requested = new ArrayList<>();

        public Transaction accept(Transaction transaction) {
            mempool.add(transaction);
            notifySubscribers(transaction);

            return transaction;
        }

        public Transaction replace(Transaction original, Transaction replacement) {
            mempool.remove(original);
            mempool.add(replacement);
            notifySubscribers(original);
            notifySubscribers(replacement);

            return replacement;
        }

        //Records the new status of each subscribed script hash the transaction pays, as SubscriptionService does on the notification for it
        private void notifySubscribers(Transaction transaction) {
            transaction.getOutputs().stream().map(ElectrumServer::getScriptHash).filter(ElectrumServer.getSubscribedScriptHashes()::containsKey)
                    .forEach(scriptHash -> ElectrumServer.updateSubscribedScriptHashStatus(scriptHash, getStatus(scriptHash)));
        }

        private List<ScriptHashTx> getHistory(String scriptHash) {
            return mempool.stream().filter(transaction -> transaction.getOutputs().stream().map(ElectrumServer::getScriptHash).anyMatch(scriptHash::equals))
                    .map(transaction -> new ScriptHashTx(0, transaction.getTxId().toString(), FEE)).toList();
        }

        private String getStatus(String scriptHash) {
            return ElectrumServer.getScriptHashStatus(getHistory(scriptHash));
        }

        @Override
        public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
            Map<String, String> result = new LinkedHashMap<>();
            pathScriptHashes.forEach((path, scriptHash) -> result.put(path, getStatus(scriptHash)));
            return result;
        }

        @Override
        public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
            Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
            pathScriptHashes.forEach((path, scriptHash) -> result.put(path, getHistory(scriptHash).toArray(new ScriptHashTx[0])));
            return result;
        }

        @Override
        public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
            requested.addAll(txids);
            Map<String, String> result = new LinkedHashMap<>();
            for(String txid : txids) {
                Transaction transaction = mempool.stream().filter(tx -> tx.getTxId().toString().equals(txid)).findFirst().orElseThrow();
                result.put(txid, Utils.bytesToHex(transaction.bitcoinSerialize()));
            }
            return result;
        }
    }

    private static class UnusedTransport extends TcpTransport {
        public UnusedTransport() {
            super(HostAndPort.fromParts("localhost", 1));
        }

        @Override
        public String pass(String request) {
            throw new UnsupportedOperationException("The fake server answers without the transport");
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }
}
