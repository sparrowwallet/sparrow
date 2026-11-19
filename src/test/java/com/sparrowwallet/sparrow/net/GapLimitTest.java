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
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GapLimitTest {
    private static final long FEE = 200L;
    private static final int MAX_INDEX = 100;

    @TempDir
    private static Path tempHome;

    private ServerType previousServerType;
    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ServerCapability previousServerCapability;

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
    public void statusWithoutHistoryDoesNotExtendGap() throws Exception {
        Wallet wallet = softwareWallet();
        ElectrumServer.electrumServerRpc = new HistoryServer(Map.of(), true);
        refresh(wallet);
        assertEquals(wallet.getGapLimit(), receiveChildren(wallet));
    }

    @Test
    public void transactionNotPayingAddressDoesNotExtendGap() throws Exception {
        Wallet wallet = softwareWallet();
        Transaction unrelated = new Transaction();
        unrelated.addInput(Sha256Hash.wrap(String.format("%064x", 0xc0)), 0, new Script(new byte[0]));
        unrelated.addOutput(10_000L, softwareWallet(2).getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress());
        ElectrumServer.electrumServerRpc = new HistoryServer(Map.of(), true, unrelated);
        refresh(wallet);
        assertEquals(wallet.getGapLimit(), receiveChildren(wallet));
    }

    @Test
    public void paymentExtendsGap() throws Exception {
        Wallet wallet = softwareWallet();
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(wallet, 15);
        WalletNode paid = wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getIndex() == 15).findFirst().orElseThrow();
        Transaction payment = new Transaction();
        payment.addInput(Sha256Hash.wrap(String.format("%064x", 0xc1)), 0, new Script(new byte[0]));
        payment.addOutput(10_000L, paid.getAddress());
        ElectrumServer.electrumServerRpc = new HistoryServer(Map.of(ElectrumServer.getScriptHash(paid), payment), false);
        refresh(wallet);
        assertEquals(15 + wallet.getGapLimit() + 1, receiveChildren(wallet));
    }

    private static void refresh(Wallet wallet) throws ServerException {
        assertTrue(new ElectrumServer().fetchAndCalculateHistory(wallet, null, null));
    }

    private static int receiveChildren(Wallet wallet) {
        return wallet.getNode(KeyPurpose.RECEIVE).getChildren().size();
    }

    private static Wallet softwareWallet() throws MnemonicException {
        return softwareWallet(1);
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
     * Reports the given payments to the script hashes they pay. With reportAll, every other script hash is given a status too, with the
     * transactions in unreferenced as its history, or none. Refuses to answer for an address past MAX_INDEX, so that a gap extended without
     * bound fails rather than runs on.
     */
    private static class HistoryServer extends SimpleElectrumServerRpc {
        private final Map<String, Transaction> payments;
        private final boolean reportAll;
        private final List<Transaction> unreferenced;

        public HistoryServer(Map<String, Transaction> payments, boolean reportAll, Transaction... unreferenced) {
            this.payments = payments;
            this.reportAll = reportAll;
            this.unreferenced = List.of(unreferenced);
        }

        private List<ScriptHashTx> getHistory(String scriptHash) {
            if(payments.containsKey(scriptHash)) {
                return List.of(new ScriptHashTx(0, payments.get(scriptHash).getTxId().toString(), FEE));
            }

            return reportAll ? unreferenced.stream().map(tx -> new ScriptHashTx(0, tx.getTxId().toString(), FEE)).toList() : List.of();
        }

        private void checkIndex(String path) {
            int index = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
            if(index > MAX_INDEX) {
                throw new IllegalStateException("Asked for address " + path + " beyond index " + MAX_INDEX);
            }
        }

        @Override
        public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
            Map<String, String> result = new LinkedHashMap<>();
            pathScriptHashes.forEach((path, scriptHash) -> {
                checkIndex(path);
                boolean reported = payments.containsKey(scriptHash) || reportAll;
                result.put(path, reported ? Utils.bytesToHex(Sha256Hash.hash(scriptHash.getBytes())) : null);
            });
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
            Map<String, String> result = new LinkedHashMap<>();
            List<Transaction> all = new ArrayList<>(payments.values());
            all.addAll(unreferenced);
            for(String txid : txids) {
                Transaction transaction = all.stream().filter(tx -> tx.getTxId().toString().equals(txid)).findFirst().orElseThrow();
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
