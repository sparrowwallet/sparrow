package com.sparrowwallet.sparrow.net;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.UnsignedTransactionsEvent;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which unconfirmed transactions spending from a wallet reach its history. Until it is mined a transaction is only what the server reports, so where
 * it spends the wallet's own outputs it must carry the wallet's signatures for them.
 */
public class UnconfirmedSpendTest {
    private static final int FUNDING_HEIGHT = 800000;

    private static final String FOREIGN_TXID = "1100000000000000000000000000000000000000000000000000000000000000";

    @TempDir
    private static Path tempHome;

    private ServerType previousServerType;
    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ServerCapability previousServerCapability;
    private RecordingServer server;

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
        previousServerType = Config.get().getServerType();
        Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
        previousElectrumServerRpc = ElectrumServer.electrumServerRpc;
        previousTransport = ElectrumServer.transport;
        previousServerCapability = ElectrumServer.serverCapability;
        server = new RecordingServer();
        ElectrumServer.electrumServerRpc = server;
        ElectrumServer.transport = new UnusedTransport();
        ElectrumServer.serverCapability = null;
    }

    @AfterEach
    public void tearDown() {
        Config.get().setServerType(previousServerType);
        ElectrumServer.electrumServerRpc = previousElectrumServerRpc;
        ElectrumServer.transport = previousTransport;
        ElectrumServer.serverCapability = previousServerCapability;
        ElectrumServer.clearPreviousServerState();
    }

    @Test
    public void keepsASignedSpend() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        Transaction funding = fund(wallet);
        Transaction spend = signedSpend(wallet);

        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = history(wallet, funding, spend);
        getReferencedTransactions(wallet, nodeTransactionMap);

        assertTrue(holds(nodeTransactionMap, spend));
        assertTrue(wallet.getTransactions().containsKey(spend.getTxId()));
    }

    @Test
    public void leavesOutASpendWithAlteredOutputs() throws Exception {
        for(ScriptType scriptType : List.of(ScriptType.P2PKH, ScriptType.P2SH_P2WPKH, ScriptType.P2WPKH, ScriptType.P2TR)) {
            Wallet wallet = signingWallet(scriptType);
            Transaction funding = fund(wallet);
            Transaction altered = alterOutputs(signedSpend(wallet));

            Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = history(wallet, funding, altered);
            getReferencedTransactions(wallet, nodeTransactionMap);

            assertFalse(holds(nodeTransactionMap, altered), scriptType + " spend must be left out");
            assertTrue(holds(nodeTransactionMap, funding), scriptType + " funding must be kept");
            new ElectrumServer().calculateNodeHistory(wallet, nodeTransactionMap);
            assertTrue(wallet.getWalletUtxos().keySet().stream().anyMatch(utxo -> utxo.getHash().equals(funding.getTxId())), scriptType + " output must be unspent");
        }
    }

    @Test
    public void leavesOutWhatSpendsALeftOutTransaction() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        fund(wallet);
        Transaction altered = alterOutputs(signedSpend(wallet));

        Transaction child = new Transaction();
        child.addInput(altered.getTxId(), 0, new Script(new byte[0]));
        child.addOutput(100_000L, wallet.getAddress(wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next()));

        Map<Sha256Hash, Transaction> unconfirmed = new LinkedHashMap<>();
        unconfirmed.put(child.getTxId(), child);
        unconfirmed.put(altered.getTxId(), altered);

        Set<Sha256Hash> unsigned = new ElectrumServer().getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));

        assertEquals(Set.of(altered.getTxId(), child.getTxId()), unsigned);
    }

    @Test
    public void keepsAnIncomingPayment() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2TR);
        Transaction incoming = new Transaction();
        incoming.addInput(Sha256Hash.wrap(FOREIGN_TXID), 0, new Script(new byte[0]));
        incoming.addOutput(100_000L, wallet.getAddress(wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next()));

        Set<Sha256Hash> unsigned = new ElectrumServer().getUnsignedTransactions(wallet, Map.of(incoming.getTxId(), incoming), txid -> body(wallet, txid));

        assertTrue(unsigned.isEmpty());
        assertTrue(server.requested.isEmpty());
    }

    @Test
    public void fetchesTheTransactionsATaprootSpendCommitsTo() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2TR);
        fund(wallet);
        Transaction signed = signedSpend(wallet);
        Transaction withForeignInput = new Transaction(signed.bitcoinSerialize());
        withForeignInput.addInput(Sha256Hash.wrap(FOREIGN_TXID), 0, new Script(new byte[0]));

        Set<Sha256Hash> unsigned = new ElectrumServer().getUnsignedTransactions(wallet, Map.of(withForeignInput.getTxId(), withForeignInput), txid -> body(wallet, txid));

        assertEquals(Set.of(FOREIGN_TXID), server.requested);
        assertEquals(Set.of(withForeignInput.getTxId()), unsigned);
    }

    @Test
    public void fetchesTheTransactionsATaprootSpendCommitsToOncePerPass() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2TR);
        Transaction funding = fund(wallet);
        WalletNode addressNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();

        //A payjoin: the other party's input sits beside the wallet's, and the wallet's taproot signature commits to the output it spends too
        Transaction foreignParent = new Transaction();
        foreignParent.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        foreignParent.addOutput(50_000L, ScriptType.P2WPKH.getOutputScript(Utils.hexToBytes("bb00000000000000000000000000000000000022")));
        server.serve(foreignParent);

        Transaction payjoin = new Transaction();
        payjoin.setVersion(2);
        payjoin.setSegwitFlag(Transaction.DEFAULT_SEGWIT_FLAG);
        payjoin.addInput(funding.getTxId(), 0, new Script(new byte[0]));
        payjoin.addInput(foreignParent.getTxId(), 0, new Script(new byte[0]));
        payjoin.addOutput(1_040_000L, ScriptType.P2WPKH.getOutputScript(Utils.hexToBytes("cc00000000000000000000000000000000000033")));
        List<TransactionOutput> spentOutputs = List.of(funding.getOutputs().getFirst(), foreignParent.getOutputs().getFirst());
        Sha256Hash hash = payjoin.hashForTaprootSignature(spentOutputs, 0, false, funding.getOutputs().getFirst().getScript(), SigHash.DEFAULT, null);
        ECKey privKey = ScriptType.P2TR.getOutputKey(PolicyType.SINGLE_HD, wallet.getKeystores().getFirst().getKey(addressNode));
        payjoin.getInputs().getFirst().setWitness(new TransactionWitness(payjoin, privKey.sign(hash, SigHash.DEFAULT, TransactionSignature.Type.SCHNORR)));
        payjoin = new Transaction(payjoin.bitcoinSerialize());
        Map<Sha256Hash, Transaction> unconfirmed = Map.of(payjoin.getTxId(), payjoin);

        ElectrumServer electrumServer = new ElectrumServer();
        Set<Sha256Hash> first = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
        assertTrue(first.isEmpty());
        assertEquals(Set.of(foreignParent.getTxId().toString()), server.requested);

        server.requested.clear();
        Set<Sha256Hash> second = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
        assertTrue(second.isEmpty());
        assertTrue(server.requested.isEmpty());
    }

    @Test
    public void doesNotFetchAgainForASpendAlreadyLeftOut() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2TR);
        fund(wallet);
        Transaction signed = signedSpend(wallet);
        Transaction withForeignInput = new Transaction(signed.bitcoinSerialize());
        withForeignInput.addInput(Sha256Hash.wrap(FOREIGN_TXID), 0, new Script(new byte[0]));
        Map<Sha256Hash, Transaction> unconfirmed = Map.of(withForeignInput.getTxId(), withForeignInput);

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
        server.requested.clear();
        Set<Sha256Hash> unsigned = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));

        assertEquals(Set.of(withForeignInput.getTxId()), unsigned);
        assertTrue(server.requested.isEmpty());
    }

    @Test
    public void judgesASpendAgainOnceItsFundingTransactionIsKnown() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        Transaction funding = fund(wallet);
        Transaction altered = alterOutputs(signedSpend(wallet));
        Map<Sha256Hash, Transaction> unconfirmed = Map.of(altered.getTxId(), altered);

        //The first call of a pass may reach a spend before the transaction it spends, when that arrives with the history of another node
        ElectrumServer electrumServer = new ElectrumServer();
        Set<Sha256Hash> before = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> txid.equals(funding.getTxId()) ? null : body(wallet, txid));
        Set<Sha256Hash> after = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));

        assertTrue(before.isEmpty());
        assertEquals(Set.of(altered.getTxId()), after);
    }

    @Test
    public void judgesASpendAgainOnceTheAddressItSpendsFromIsDerived() throws Exception {
        //A restore derives addresses in rounds up to the gap limit, so a spend reached before the address it spends from is derived must be judged again
        Wallet derived = signingWallet(ScriptType.P2WPKH);
        derived.getNode(KeyPurpose.RECEIVE).fillToIndex(30);
        WalletNode addressNode = derived.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getIndex() == 30).findFirst().orElseThrow();
        Transaction funding = new Transaction();
        funding.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        funding.addOutput(1_000_000L, derived.getAddress(addressNode));
        derived.updateTransactions(Map.of(funding.getTxId(), new BlockTransaction(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, funding)));
        addressNode.getTransactionOutputs().add(new BlockTransactionHashIndex(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, 0, 1_000_000L));
        Transaction altered = alterOutputs(signedSpend(derived));

        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(19);
        wallet.updateTransactions(Map.of(funding.getTxId(), new BlockTransaction(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, funding)));
        Map<Sha256Hash, Transaction> unconfirmed = Map.of(altered.getTxId(), altered);

        ElectrumServer electrumServer = new ElectrumServer();
        Set<Sha256Hash> before = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(30);
        Set<Sha256Hash> after = electrumServer.getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));

        assertTrue(before.isEmpty());
        assertEquals(Set.of(altered.getTxId()), after);
    }

    @Test
    public void warnsOncePerServer() throws Exception {
        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        fund(wallet);
        Transaction altered = alterOutputs(signedSpend(wallet));
        Map<Sha256Hash, Transaction> unconfirmed = Map.of(altered.getTxId(), altered);

        List<Set<Sha256Hash>> warnings = new ArrayList<>();
        Object listener = new Object() {
            @Subscribe
            public void unsignedTransactions(UnsignedTransactionsEvent event) {
                warnings.add(event.getTxids());
            }
        };
        EventManager.get().register(listener);
        try {
            new ElectrumServer().getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
            new ElectrumServer().getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
            assertEquals(1, warnings.size());

            //Switching servers asks the next one afresh, so the same transaction reported by it is raised again
            ElectrumServer.clearPreviousServerState();
            new ElectrumServer().getUnsignedTransactions(wallet, unconfirmed, txid -> body(wallet, txid));
            assertEquals(List.of(Set.of(altered.getTxId()), Set.of(altered.getTxId())), warnings);
        } finally {
            EventManager.get().unregister(listener);
        }
    }

    @Test
    public void doesNotCheckOnABitcoinCoreConnection() throws Exception {
        Config.get().setServerType(ServerType.BITCOIN_CORE);
        Wallet wallet = signingWallet(ScriptType.P2WPKH);
        Transaction funding = fund(wallet);
        Transaction altered = alterOutputs(signedSpend(wallet));

        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = history(wallet, funding, altered);
        getReferencedTransactions(wallet, nodeTransactionMap);

        assertTrue(holds(nodeTransactionMap, altered));
    }

    private static void getReferencedTransactions(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);
    }

    /**
     * The history the server reports for the funded address, with the spend unconfirmed. The wallet already holds both transactions at those heights,
     * so nothing is fetched.
     */
    private static Map<WalletNode, Set<BlockTransactionHash>> history(Wallet wallet, Transaction funding, Transaction spend) {
        wallet.updateTransactions(Map.of(spend.getTxId(), new BlockTransaction(spend.getTxId(), 0, null, 1000L, spend)));
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new LinkedHashMap<>();
        nodeTransactionMap.put(node, new TreeSet<>(List.of(new BlockTransaction(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, null),
                new BlockTransaction(spend.getTxId(), 0, null, 1000L, null))));

        return nodeTransactionMap;
    }

    private static boolean holds(Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, Transaction transaction) {
        return nodeTransactionMap.values().stream().flatMap(Collection::stream).anyMatch(reference -> reference.getHash().equals(transaction.getTxId()));
    }

    private static Transaction alterOutputs(Transaction signed) {
        Transaction altered = new Transaction(signed.bitcoinSerialize());
        altered.getOutputs().getFirst().setScriptBytes(ScriptType.P2WPKH.getOutputScript(Utils.hexToBytes("bb00000000000000000000000000000000000022")).getProgram());
        altered = new Transaction(altered.bitcoinSerialize());
        assertNotEquals(signed.getTxId(), altered.getTxId());

        return altered;
    }

    private static Transaction fund(Wallet wallet) {
        WalletNode addressNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        Transaction funding = new Transaction();
        funding.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        funding.addOutput(1_000_000L, wallet.getAddress(addressNode));
        wallet.updateTransactions(Map.of(funding.getTxId(), new BlockTransaction(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, funding)));
        addressNode.getTransactionOutputs().add(new BlockTransactionHashIndex(funding.getTxId(), FUNDING_HEIGHT, new Date(), 0L, 0, 1_000_000L));

        return funding;
    }

    private static Transaction signedSpend(Wallet wallet) throws Exception {
        WalletNode addressNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        TransactionParameters params = new TransactionParameters(List.of(new PriorityUtxoSelector(FUNDING_HEIGHT + 6)), Collections.emptyList(),
                List.of(new Payment(wallet.getAddress(addressNode), "test", 500_000L, false)), Collections.emptyList(),
                Collections.emptySet(), 10.0d, 1.0d, 1.0d, null, FUNDING_HEIGHT + 6, false, false, true);
        PSBT psbt = wallet.createWalletTransaction(params).createPSBT();
        wallet.sign(psbt);
        wallet.finalise(psbt);

        return psbt.extractTransaction();
    }

    private static Wallet signingWallet(ScriptType scriptType) throws MnemonicException {
        Wallet wallet = new Wallet("test");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(scriptType);
        DeterministicSeed seed = new DeterministicSeed(Utils.hexToBytes(String.format("%032x", 1)), "", 0);
        Keystore keystore = Keystore.fromSeed(seed, PolicyType.SINGLE_HD, scriptType.getDefaultDerivation());
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, scriptType, wallet.getKeystores(), null));
        wallet.setStoredBlockHeight(FUNDING_HEIGHT + 6);
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(0);

        return wallet;
    }

    private static Transaction body(Wallet wallet, Sha256Hash txid) {
        BlockTransaction blockTransaction = wallet.getWalletTransaction(txid);
        return blockTransaction == null ? null : blockTransaction.getTransaction();
    }

    private static class RecordingServer extends SimpleElectrumServerRpc {
        private final Set<String> requested = new LinkedHashSet<>();
        private final Map<String, String> served = new HashMap<>();

        public void serve(Transaction transaction) {
            served.put(transaction.getTxId().toString(), Utils.bytesToHex(transaction.bitcoinSerialize()));
        }

        @Override
        public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
            requested.addAll(txids);
            Map<String, String> result = new LinkedHashMap<>();
            txids.forEach(txid -> result.put(txid, served.getOrDefault(txid, Sha256Hash.ZERO_HASH.toString())));
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
