package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.TransactionProofsFailedEvent;
import com.sparrowwallet.sparrow.event.TransactionProofsRefusedEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The write boundary against a server that answers proofs as the test chooses: which confirmed heights are written, which are demoted to unconfirmed,
 * and which of the two dialogs each outcome raises. Nothing here is exercised by an honest server, which is the reason it is covered so closely - a
 * demotion that misfires costs a user their confirmed history, and a refusal reported as dishonesty accuses a server that did nothing wrong.
 * <p>
 * Regtest is the network whose trivial target a synthetic chain can be mined against, and its empty checkpoints anchor the store at genesis, so every
 * height below is one the store itself serves.
 */
public class TransactionProofTest {
    //The same extended key in each encoding, since the wallet only has to derive addresses to have script hashes
    private static final String TEST_TPUB = "tpubDCBWBScQPGv4Xk3JSbhw6wYYpayMjb2eAYyArpbSqQTbLDpphHGAetB6VQgVeftLML8vDSUEWcC2xDi3qJJ3YCDChJDvqVzpgoYSuT52MhJ";

    private static final String TEST_XPUB = "xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj";

    private static final long CHAIN_TIME = 1600000000L;

    private static final int CHAIN_LENGTH = 10;

    //The block whose transactions are proven, with room above it for a height the store does not reach
    private static final int PROVEN_HEIGHT = 5;

    private static final long RECENT_FEE = 1000L;

    @TempDir
    private static Path tempHome;

    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ServerCapability previousServerCapability;
    private int previousProofAttempts;
    private long previousProofRetryDelayMillis;
    private ProofListener listener;

    private List<Transaction> blockTransactions;
    private List<BlockHeader> chain;
    private FakeProofServer server;

    @BeforeAll
    public static void setUpAll() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() throws Exception {
        Network.set(Network.REGTEST);
        File[] files = Storage.getHeadersDir().listFiles();
        if(files != null) {
            for(File file : files) {
                assertTrue(file.delete());
            }
        }

        ElectrumServer.headerStore = null;
        ElectrumServer.lastReorgForkHeight = Integer.MAX_VALUE;
        ElectrumServer.verifiedHistoricalHeaders.clear();
        ElectrumServer.clearPreviousServerState();
        ElectrumServer.getSubscribedScriptHashes().clear();
        previousElectrumServerRpc = ElectrumServer.electrumServerRpc;
        previousTransport = ElectrumServer.transport;
        previousServerCapability = ElectrumServer.serverCapability;
        previousProofAttempts = ElectrumServer.proofAttempts;
        previousProofRetryDelayMillis = ElectrumServer.proofRetryDelayMillis;
        ElectrumServer.transport = new UnusedTransport();
        ElectrumServer.serverCapability = new ServerCapability(false, false, false);
        ElectrumServer.proofRetryDelayMillis = 0;       //the retry budget is what is under test, not how long it takes to spend
        listener = new ProofListener();
        EventManager.get().register(listener);

        //Four transactions in one block, so a branch has two levels and both concatenation orders are exercised
        blockTransactions = List.of(transaction(0), transaction(1), transaction(2), transaction(3));
        chain = mineChain(Network.REGTEST.getGenesisHeader(), CHAIN_LENGTH, merkleRoot(txids(blockTransactions)));
        server = new FakeProofServer(chain);
        ElectrumServer.electrumServerRpc = server;
        seedStore(chain, CHAIN_LENGTH - 1);     //one height short of the chain, so a proof above the store tip has somewhere to fail
    }

    @AfterEach
    public void tearDown() {
        EventManager.get().unregister(listener);
        ElectrumServer.electrumServerRpc = previousElectrumServerRpc;
        ElectrumServer.transport = previousTransport;
        ElectrumServer.serverCapability = previousServerCapability;
        ElectrumServer.proofAttempts = previousProofAttempts;
        ElectrumServer.proofRetryDelayMillis = previousProofRetryDelayMillis;
        ElectrumServer.headerStore = null;
        ElectrumServer.lastReorgForkHeight = Integer.MAX_VALUE;
        ElectrumServer.verifiedHistoricalHeaders.clear();
        //Every wallet here derives from the same key, so one test's cached script hash state is the next one's, and the caches are keyed by script hash
        ElectrumServer.clearPreviousServerState();
        ElectrumServer.getSubscribedScriptHashes().clear();
        ElectrumServer.confirmingRecent.clear();
        AppServices.setAnnouncedTip(null);
        Network.set(null);
    }

    /**
     * The whole path for one newly confirmed transaction: the proof reconstructs the merkle root of a header the store already holds, the height is
     * written, and the hash of the block it was proven against is written with it. No block header is fetched, which is what leaves a real
     * confirmation making the same single call a recent transaction's confirmation makes.
     */
    @Test
    public void provesAConfirmedTransactionAndRecordsItsBlock() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);

        BlockTransaction written = wallet.getWalletTransaction(transaction.getTxId());
        assertNotNull(written);
        assertEquals(PROVEN_HEIGHT, written.getHeight());
        assertEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), written.getBlockHash());
        assertEquals(1, server.getProofRequests());
        assertEquals(0, server.getBlockHeaderRequests());
        assertTrue(listener.isEmpty());
    }

    /**
     * A branch that does not reconstruct the root of a verified header is the server proven wrong, which is the one outcome the dishonesty wording is
     * reserved for. The transaction stays in the wallet as unconfirmed rather than being removed.
     */
    @Test
    public void demotesATamperedProof() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        TransactionMerkleProof proof = server.serveProof(transaction, PROVEN_HEIGHT, 0);
        proof.merkle.set(0, Sha256Hash.ZERO_HASH.toString());
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(0, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertNull(wallet.getWalletTransaction(transaction.getTxId()).getBlockHash());
        assertEquals(Set.of(reference(transaction, 0)), nodeTransactionMap.values().iterator().next());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), listener.getFailed());
        assertTrue(listener.getRefused().isEmpty());
    }

    /**
     * The CVE-2017-12842 forgery: a fake transaction whose txid is one half of a genuine 64 byte transaction mined in the block, with the other half
     * presented as its sibling. The verifier rejects it because that concatenation deserializes as a transaction, which no inner node of a real tree
     * can. A rejected branch is a proof that does not verify rather than an exception escaping the pass.
     */
    @Test
    public void rejectsABranchWhoseInnerNodeIsATransaction() {
        Transaction sixtyFourByteTransaction = new Transaction();
        sixtyFourByteTransaction.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        sixtyFourByteTransaction.addOutput(0, new Script(new byte[] {0x51, 0x51, 0x51, 0x51}));
        byte[] serialized = sixtyFourByteTransaction.bitcoinSerialize();
        assertEquals(64, serialized.length);

        Sha256Hash forgedLeaf = Sha256Hash.wrapReversed(Arrays.copyOfRange(serialized, 0, 32));
        TransactionMerkleProof proof = new TransactionMerkleProof();
        proof.block_height = PROVEN_HEIGHT;
        proof.pos = 0;
        proof.merkle = List.of(Sha256Hash.wrapReversed(Arrays.copyOfRange(serialized, 32, 64)).toString());

        assertFalse(ElectrumServer.verifyProof(forgedLeaf, proof, chain.get(PROVEN_HEIGHT - 1)));
    }

    /**
     * Malformed proofs are proofs that do not verify, never exceptions escaping the pass.
     */
    @Test
    public void rejectsAMalformedBranch() {
        BlockHeader header = chain.get(PROVEN_HEIGHT - 1);
        TransactionMerkleProof proof = new TransactionMerkleProof();
        proof.block_height = PROVEN_HEIGHT;
        proof.pos = 0;

        proof.merkle = null;
        assertFalse(ElectrumServer.verifyProof(blockTransactions.getFirst().getTxId(), proof, header));

        proof.merkle = Collections.singletonList(null);
        assertFalse(ElectrumServer.verifyProof(blockTransactions.getFirst().getTxId(), proof, header));

        proof.merkle = List.of("cafebabe");
        assertFalse(ElectrumServer.verifyProof(blockTransactions.getFirst().getTxId(), proof, header));
    }

    /**
     * A proof answered for another block is the server declining to substantiate the height it reported, not the server caught lying about it: Electrum
     * treats the requested height as a hint, and the wording the user is shown has to reflect what has actually been shown.
     */
    @Test
    public void refusesAProofForAnotherBlock() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        TransactionMerkleProof proof = server.serveProof(transaction, PROVEN_HEIGHT, 0);
        proof.block_height = PROVEN_HEIGHT + 1;
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(0, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), listener.getRefused());
        assertTrue(listener.getFailed().isEmpty());
        assertEquals(ElectrumServer.proofAttempts, server.getProofRequests());      //it stays outstanding, so the whole retry budget is spent on it
    }

    /**
     * A branch deeper than a block can hold is refused by the verifier's own bound before any hashing is done.
     */
    @Test
    public void demotesAnOverDeepBranch() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        TransactionMerkleProof proof = server.serveProof(transaction, PROVEN_HEIGHT, 0);
        proof.merkle = new ArrayList<>(Collections.nCopies(16, Sha256Hash.ZERO_HASH.toString()));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(0, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(1, listener.getFailed().size());
    }

    /**
     * Where Sparrow cannot reach a verified header at the height, nothing has been shown to be false: the server may be honest and simply unable to
     * serve the range. That is refusal class, however good the proof it supplied looks.
     */
    @Test
    public void refusesAHeightTheStoreCannotReach() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, CHAIN_LENGTH, 0);
        //The store stops one short of the height, and the server will not serve the header that would advance it
        server.setServedChainLength(CHAIN_LENGTH - 1);
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, CHAIN_LENGTH));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(0, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(Set.of(reference(transaction, CHAIN_LENGTH)), listener.getRefused());
        assertTrue(listener.getFailed().isEmpty());
    }

    /**
     * The discrimination is behavioural: a server refusing for capacity fails whole batches and recovers, while one that cannot substantiate a
     * particular pair leaves that pair unanswered while its siblings succeed. Only the second raises a dialog, and only for the pair it concerns.
     */
    @Test
    public void reportsAPersistentRefusalAmongSucceedingSiblings() throws Exception {
        Wallet wallet = testWallet();
        List<Transaction> transactions = blockTransactions.subList(0, 3);
        for(int i = 0; i < transactions.size(); i++) {
            server.serveTransaction(transactions.get(i));
            if(i > 0) {
                server.serveProof(transactions.get(i), PROVEN_HEIGHT, i);
            }
        }

        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet,
                transactions.stream().map(transaction -> reference(transaction, PROVEN_HEIGHT)).toArray(BlockTransactionHash[]::new));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transactions.get(1).getTxId()).getHeight());
        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transactions.get(2).getTxId()).getHeight());
        assertEquals(0, wallet.getWalletTransaction(transactions.getFirst().getTxId()).getHeight());
        assertEquals(Set.of(reference(transactions.getFirst(), PROVEN_HEIGHT)), listener.getRefused());
        assertTrue(listener.getFailed().isEmpty());
        //The siblings are answered on the first attempt, and only the pair that stays outstanding is asked for again
        assertEquals(ElectrumServer.proofAttempts, server.getProofRequests());
        assertEquals(3, server.getProofRequestKeys().getFirst().size());
        assertEquals(1, server.getProofRequestKeys().getLast().size());
    }

    /**
     * A refusal that clears on a retry is the shape a server under load has, and it must cost nothing but the wait.
     */
    @Test
    public void retriesATransientRefusal() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);
        server.refuseFirstAttempts(transaction, PROVEN_HEIGHT, 1);
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(2, server.getProofRequests());
        assertTrue(listener.isEmpty());
    }

    /**
     * A server momentarily unable to answer the call at all looks exactly like one that never will, until the retries have been spent. The first
     * attempt failing as a whole therefore costs a retry and nothing else.
     */
    @Test
    public void retriesAWholeCallThatFailsOnce() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);
        server.failFirstRequests(1, new ElectrumServerRpcException("Server busy"));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(2, server.getProofRequests());
        assertTrue(ElectrumServer.isVerifyingTransactions());
        assertTrue(listener.isEmpty());
    }

    /**
     * A private server that cannot serve the call at all, for any reason other than not implementing it, must not cost the user their wallet history:
     * it is a server they chose and already trust for everything else it reports. The session goes unverified from here, and nothing is demoted, since
     * a call that failed as a whole has refused nothing.
     */
    @Test
    public void proceedsUnverifiedWhereAPrivateServerCannotSupplyProofs() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.setProofFailure(new ElectrumServerRpcException("Batch too large"));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertFalse(ElectrumServer.isVerifyingTransactions());
        assertEquals(ElectrumServer.proofAttempts, server.getProofRequests());       //only after the retries are spent
        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), nodeTransactionMap.values().iterator().next());
        assertTrue(listener.isEmpty());
    }

    /**
     * The public tier does not get that leniency: verification is what the server is there for, so the history fails and another server is tried.
     */
    @Test
    public void failsTheHistoryWhereAPublicServerCannotSupplyProofs() throws Exception {
        Network.set(Network.MAINNET);
        ServerType previousServerType = Config.get().getServerType();
        Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
        try {
            Wallet wallet = testWallet();
            Transaction transaction = blockTransactions.getFirst();
            server.setProofFailure(new ElectrumServerRpcException("Batch too large"));
            int height = Network.MAINNET.getHeaderCheckpoints().getMaxHeight() + 1;
            Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, height));

            ElectrumServer electrumServer = new ElectrumServer();
            assertThrows(ServerException.class, () -> electrumServer.getReferencedTransactions(wallet, nodeTransactionMap));

            assertTrue(ElectrumServer.isVerifyingTransactions());
            assertNull(wallet.getWalletTransaction(transaction.getTxId()));
        } finally {
            Config.get().setServerType(previousServerType);
        }
    }

    /**
     * A lost connection reaches this as the same exception and must not be read as the server being unable to serve the call: it is the ordinary
     * reconnect's business, and downgrading the session for it would let one network failure leave a wallet unverified until the next connect.
     */
    @Test
    public void failsThePassWhereTheConnectionIsGoneRatherThanDisablingVerification() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.setProofFailure(new ElectrumServerRpcException("Connection closed"));
        ElectrumServer.transport = new UnusedTransport(false);
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        assertThrows(ServerException.class, () -> electrumServer.getReferencedTransactions(wallet, nodeTransactionMap));

        assertTrue(ElectrumServer.isVerifyingTransactions());
        assertNull(wallet.getWalletTransaction(transaction.getTxId()));
    }

    /**
     * A stored height carries its verdict: it was proven when it was written, or it predates the feature, and either way it is never proven again.
     * This is what keeps a refresh, a restart and a server switch from re-proving a whole wallet.
     */
    @Test
    public void doesNotReproveAStoredHeight() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        wallet.updateTransactions(Map.of(transaction.getTxId(),
                new BlockTransaction(transaction.getTxId(), PROVEN_HEIGHT, null, 0L, transaction, chain.get(PROVEN_HEIGHT - 1).getHash())));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);

        assertEquals(0, server.getProofRequests());
        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
    }

    /**
     * The input a lying server would craft: one transaction reported at two heights on two script hashes. Keyed by transaction alone the pair that did
     * not prove rides into the wallet on the back of the pair that did, so each is proven and demoted on its own, and the proven one wins the collapse
     * to a single wallet transaction.
     */
    @Test
    public void demotesTheUnprovenPairOfATransactionReportedAtTwoHeights() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);       //the other height is not answered at all

        List<WalletNode> nodes = new ArrayList<>(wallet.getNode(KeyPurpose.RECEIVE).getChildren());
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new LinkedHashMap<>();
        nodeTransactionMap.put(nodes.get(0), new TreeSet<>(Set.of(reference(transaction, PROVEN_HEIGHT))));
        nodeTransactionMap.put(nodes.get(1), new TreeSet<>(Set.of(reference(transaction, PROVEN_HEIGHT + 1))));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), wallet.getWalletTransaction(transaction.getTxId()).getBlockHash());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), nodeTransactionMap.get(nodes.get(0)));
        assertEquals(Set.of(reference(transaction, 0)), nodeTransactionMap.get(nodes.get(1)));
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT + 1)), listener.getRefused());
    }

    /**
     * The gate runs many times in one pass over overlapping reference sets, and a demoted height reads as changed to every later call. Without the memo
     * each of those calls would spend the retry budget on it again; with it the later calls demote and move on, and the next pass re-attempts it.
     */
    @Test
    public void doesNotReproveAPairAlreadyRefusedInThePass() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);

        ElectrumServer electrumServer = new ElectrumServer();
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        int afterFirstCall = server.getProofRequests();

        //The server reports the same height again, as it does on every later call in the pass
        nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        assertEquals(afterFirstCall, server.getProofRequests());
        assertEquals(Set.of(reference(transaction, 0)), nodeTransactionMap.values().iterator().next());

        //A new pass constructs its own ElectrumServer, which starts clean
        nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));
        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);
        assertEquals(afterFirstCall * 2, server.getProofRequests());
    }

    /**
     * The same height form of a reorg, which is the one the server reports nothing about: the height is still what it was, but the block it was proven
     * against is no longer on the chain. The recorded block hash is what selects it, and only above the deepest fork this session has seen.
     */
    @Test
    public void reprovesATransactionProvenAgainstAnOrphanedHeader() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveProof(transaction, PROVEN_HEIGHT, 0);
        wallet.updateTransactions(Map.of(transaction.getTxId(),
                new BlockTransaction(transaction.getTxId(), PROVEN_HEIGHT, null, 0L, transaction, Sha256Hash.ZERO_HASH)));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        //Below the deepest fork accepted this session, so a stale block hash is not looked for at all
        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);
        assertEquals(0, server.getProofRequests());

        ElectrumServer.lastReorgForkHeight = PROVEN_HEIGHT - 1;
        new ElectrumServer().getReferencedTransactions(wallet, nodeTransactionMap);

        assertEquals(1, server.getProofRequests());
        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), wallet.getWalletTransaction(transaction.getTxId()).getBlockHash());
    }

    /**
     * One dialog per wallet per task, however many gated calls the pass makes, and however many transactions each surfaces. A pair already shown is
     * not shown again by the passes that re-attempt it.
     */
    @Test
    public void raisesOneEventPerWalletForTheWholeTask() throws Exception {
        Wallet wallet = testWallet();
        Transaction first = blockTransactions.get(0);
        Transaction second = blockTransactions.get(1);
        server.serveTransaction(first);
        server.serveTransaction(second);

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, references(wallet, reference(first, PROVEN_HEIGHT)));
        electrumServer.getReferencedTransactions(wallet, references(wallet, reference(second, PROVEN_HEIGHT)));
        electrumServer.postProofEvents(wallet);

        assertEquals(1, listener.getRefusedEvents());
        assertEquals(Set.of(reference(first, PROVEN_HEIGHT), reference(second, PROVEN_HEIGHT)), listener.getRefused());

        //The next task re-attempts both, and raises nothing for what has already been shown
        listener.reset();
        ElectrumServer nextTask = new ElectrumServer();
        nextTask.getReferencedTransactions(wallet, references(wallet, reference(first, PROVEN_HEIGHT)));
        nextTask.postProofEvents(wallet);
        assertEquals(0, listener.getRefusedEvents());
    }

    /**
     * A refusal and a proof shown false are different claims, and the weaker one must not filter out the stronger. A server that leaves a pair
     * unanswered for a pass and then supplies a branch that does not reconstruct has been proven wrong, which is the only thing the dishonesty wording
     * is for - and the user has to be told, even though the same pair already produced a refusal dialog. The converse is not true.
     */
    @Test
    public void showsAProofShownFalseAfterTheSamePairWasRefused() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);

        //The server will not answer for it at all
        ElectrumServer refusingPass = new ElectrumServer();
        refusingPass.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));
        refusingPass.postProofEvents(wallet);
        assertEquals(1, listener.getRefusedEvents());
        assertEquals(0, listener.getFailedEvents());

        //It answers on the next pass, and the branch does not reconstruct
        listener.reset();
        server.serveProof(transaction, PROVEN_HEIGHT, 0).merkle.set(0, Sha256Hash.ZERO_HASH.toString());
        ElectrumServer failingPass = new ElectrumServer();
        failingPass.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));
        failingPass.postProofEvents(wallet);

        assertEquals(1, listener.getFailedEvents());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), listener.getFailed());

        //Going quiet about it afterwards says less than what has been said, so it raises nothing
        listener.reset();
        server.refuseFirstAttempts(transaction, PROVEN_HEIGHT, Integer.MAX_VALUE);
        ElectrumServer quietPass = new ElectrumServer();
        quietPass.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));
        quietPass.postProofEvents(wallet);

        assertTrue(listener.isEmpty());
    }

    /**
     * The dialogs ask the user to switch servers, so what one server was shown to have got wrong must not filter the next one's answer for the same
     * transaction. Suppression is scoped to the connected server, and getTransport clears it on connecting to a different one.
     */
    @Test
    public void showsAFindingAgainAfterTheServerChanges() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));
        electrumServer.postProofEvents(wallet);
        assertEquals(1, listener.getRefusedEvents());

        listener.reset();
        ElectrumServer.clearPreviousServerState();      //what getTransport does on connecting to a server other than the previous one

        ElectrumServer afterSwitch = new ElectrumServer();
        afterSwitch.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));
        afterSwitch.postProofEvents(wallet);

        assertEquals(1, listener.getRefusedEvents());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), listener.getRefused());
    }

    /**
     * A private server without the call is a configuration rather than a fault: the session goes unverified from here, and nothing is demoted, since
     * nothing has been refused.
     */
    @Test
    public void disablesVerificationOnAServerWithoutTheCall() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.setProofFailure(new UnsupportedMethodException("blockchain.transaction.get_merkle", null));
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, PROVEN_HEIGHT));

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
        electrumServer.postProofEvents(wallet);

        assertTrue(!ElectrumServer.isVerifyingTransactions());
        assertEquals(PROVEN_HEIGHT, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(Set.of(reference(transaction, PROVEN_HEIGHT)), nodeTransactionMap.values().iterator().next());
        assertTrue(listener.isEmpty());
    }

    /**
     * The other half of the same finding: on the public tier verification is not optional, so a server without the call fails the history instead,
     * which is what carries it to WalletHistoryFailedEvent and rotates to another server. The capability is left on, since the next server has it.
     */
    @Test
    public void failsTheHistoryOnAPublicServerWithoutTheCall() throws Exception {
        Network.set(Network.MAINNET);
        ServerType previousServerType = Config.get().getServerType();
        Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
        try {
            Wallet wallet = testWallet();
            Transaction transaction = blockTransactions.getFirst();
            server.setProofFailure(new UnsupportedMethodException("blockchain.transaction.get_merkle", null));
            //Above the last pin, so nothing is fetched ahead of the call that is missing
            int height = Network.MAINNET.getHeaderCheckpoints().getMaxHeight() + 1;
            Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = references(wallet, reference(transaction, height));

            ElectrumServer electrumServer = new ElectrumServer();
            ServerException e = assertThrows(ServerException.class, () -> electrumServer.getReferencedTransactions(wallet, nodeTransactionMap));
            assertTrue(e.getMessage().contains("blockchain.transaction.get_merkle"));
            electrumServer.postProofEvents(wallet);

            assertTrue(ElectrumServer.isVerifyingTransactions());
            assertNull(wallet.getWalletTransaction(transaction.getTxId()));
            assertEquals(Set.of(reference(transaction, height)), nodeTransactionMap.values().iterator().next());
            assertTrue(listener.isEmpty());
        } finally {
            Config.get().setServerType(previousServerType);
        }
    }

    /**
     * The store is asked before the cache of headers the server has announced. That cache holds every tip it has ever reported and is not rewound when
     * the chain reorganises, so at a height that was once a tip it can name the replaced block - and serving that would put the orphaned block's
     * timestamp on a transaction the store has just re-proven against its replacement.
     */
    @Test
    public void servesTimestampsFromTheStoreRatherThanAnAnnouncedHeader() throws Exception {
        Wallet wallet = testWallet();
        //A different header at the proven height, as an announcement made before the block at that height was replaced would have left behind
        BlockHeader replaced = mineHeader(chain.get(PROVEN_HEIGHT - 2), Sha256Hash.ZERO_HASH, CHAIN_TIME + 900);
        assertNotEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), replaced.getHash());
        ElectrumServer.updateRetrievedBlockHeaders(PROVEN_HEIGHT, replaced);

        Map<Integer, BlockHeader> blockHeaderMap = new ElectrumServer().getBlockHeaders(wallet,
                Set.of(reference(blockTransactions.getFirst(), PROVEN_HEIGHT)));

        assertEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), blockHeaderMap.get(PROVEN_HEIGHT).getHash());
        assertEquals(0, server.getBlockHeaderRequests());
    }

    /**
     * Timestamps for a proven height come from the store the proof already used, so a confirmed wallet transaction issues no header request of its own.
     */
    @Test
    public void servesTimestampsFromTheVerifiedHeaders() throws Exception {
        Wallet wallet = testWallet();
        Transaction transaction = blockTransactions.getFirst();
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);

        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.getReferencedTransactions(wallet, references(wallet, reference(transaction, PROVEN_HEIGHT)));

        assertEquals(chain.get(PROVEN_HEIGHT - 1).getTimeAsDate(), wallet.getWalletTransaction(transaction.getTxId()).getDate());
        assertEquals(0, server.getBlockHeaderRequests());
    }

    /**
     * The silent payments batch is the second write path, and it writes heights the subscription reported without going through a history call at all.
     * The same proof applies, and the unproven reference is replaced in place by an unconfirmed one carrying whatever the batch had already found.
     */
    @Test
    public void demotesAnUnprovenSilentPaymentReference() throws Exception {
        Wallet wallet = testWallet();
        Transaction proven = blockTransactions.get(0);
        Transaction refused = blockTransactions.get(1);
        server.serveProof(proven, PROVEN_HEIGHT, 0);

        Map<BlockTransactionHash, Transaction> referencesToFetch = new TreeMap<>();
        referencesToFetch.put(reference(proven, PROVEN_HEIGHT), null);
        referencesToFetch.put(reference(refused, PROVEN_HEIGHT), refused);
        referencesToFetch.put(reference(blockTransactions.get(2), 0), null);     //unconfirmed, so nothing to prove

        ElectrumServer electrumServer = new ElectrumServer();
        Map<BlockTransactionHash, BlockHeader> result = electrumServer.verifySilentPaymentReferences(wallet, referencesToFetch);
        electrumServer.postProofEvents(wallet);

        assertEquals(Set.of(reference(proven, PROVEN_HEIGHT)), result.keySet());
        assertEquals(chain.get(PROVEN_HEIGHT - 1).getHash(), result.values().iterator().next().getHash());
        assertEquals(Set.of(reference(proven, PROVEN_HEIGHT), reference(refused, 0), reference(blockTransactions.get(2), 0)), referencesToFetch.keySet());
        assertEquals(refused, referencesToFetch.get(reference(refused, 0)));     //the demotion carries over what the batch already had
        assertEquals(Set.of(reference(refused, PROVEN_HEIGHT)), listener.getRefused());
    }

    /**
     * The whole pass on a wallet reopened with a demoted transaction already stored. The stored output sits at height 0 while the server still reports
     * it confirmed, so every used node holding it counts as changed - and where they all are, one address with one refused transaction is enough for
     * the check to read the whole history as having changed and abort into a backup and full refresh.
     * <p>
     * It would do so on every open, and the refresh would not help: the transaction is refused, demoted and stored again. For a passphrase wallet it
     * is the "incorrect passphrase" dialog, every open, until the server changes.
     */
    @Test
    public void doesNotReadAStoredDemotionAsTheWholeHistoryChanging() throws Exception {
        Wallet wallet = testWallet();
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();

        //A real payment to the wallet's one used address, so the node keeps the output the pass recalculates
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(10000L, wallet.getAddress(node));
        server.serveTransaction(transaction);

        //The wallet as the previous session left it: demoted to unconfirmed and persisted that way
        wallet.updateTransactions(Map.of(transaction.getTxId(), new BlockTransaction(transaction.getTxId(), 0, null, 0L, transaction)));
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(transaction.getTxId(), 0, null, 0L, 0, 10000));

        //The server has not changed its mind, and still will not prove it
        String scriptHash = ElectrumServer.getScriptHash(node);
        server.serveHistory(node.getDerivationPath(), new ScriptHashTx(PROVEN_HEIGHT, transaction.getTxId().toString(), 0));
        server.serveScriptHashStatus(scriptHash, ElectrumServer.getScriptHashStatus(List.of(new ScriptHashTx(PROVEN_HEIGHT, transaction.getTxId().toString(), 0))));

        ElectrumServer electrumServer = new ElectrumServer();
        assertTrue(electrumServer.fetchAndCalculateHistory(wallet, null, null));

        //Still the one used node, still unconfirmed, and nothing cleared
        assertEquals(0, wallet.getWalletTransaction(transaction.getTxId()).getHeight());
        assertEquals(1, node.getTransactionOutputs().size());
        assertEquals(0, node.getTransactionOutputs().iterator().next().getHeight());
        //The pass posts its own findings from the finally that closes it
        assertEquals(1, listener.getRefusedEvents());
    }

    /**
     * A reorg detected by the pass itself - which is what happens when a history thread reconciles from inside its own gate - invalidates nodes whose
     * data that pass has already fetched. Its status must stay cleared, or the refresh the reorg triggers finds the node looking up to date and never
     * fetches it again, leaving the transaction held against the block it was proven in rather than the one that replaced it.
     */
    @Test
    public void leavesANodeInvalidatedMidPassForTheRefreshThatFollows() throws Exception {
        Wallet wallet = testWallet();
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        String scriptHash = ElectrumServer.getScriptHash(node);
        Transaction transaction = confirmedPayment(wallet, node);
        server.invalidateDuringPass(PROVEN_HEIGHT - 1);

        new ElectrumServer().fetchAndCalculateHistory(wallet, null, null);

        assertNull(ElectrumServer.retrievedScriptHashes.get(scriptHash), "the status must not be restored over an invalidation this pass caused");
        assertTrue(ElectrumServer.reorgInvalidatedScriptHashes.contains(scriptHash), "the invalidation must survive for the refresh the reorg triggers");
    }

    /**
     * The refresh the reorg triggers is the pass that acts on the invalidation, so it does restore the status and clear it - otherwise no pass ever
     * would, and the node would be fetched again on every refresh for the rest of the session.
     */
    @Test
    public void clearsAnInvalidationTheRefreshItTriggeredHasActedOn() throws Exception {
        Wallet wallet = testWallet();
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        String scriptHash = ElectrumServer.getScriptHash(node);
        confirmedPayment(wallet, node);
        assertTrue(ElectrumServer.invalidateScriptHashesForReorg(wallet, PROVEN_HEIGHT - 1));

        new ElectrumServer().fetchAndCalculateHistory(wallet, null, null);

        assertNotNull(ElectrumServer.retrievedScriptHashes.get(scriptHash), "a pass acting on the invalidation records what it fetched");
        assertFalse(ElectrumServer.reorgInvalidatedScriptHashes.contains(scriptHash), "and the exemption lasts for exactly that one fetch");
    }

    /**
     * A payment to the node, stored as confirmed and reported so by the server, as a wallet is when a reorg reaches it.
     */
    private Transaction confirmedPayment(Wallet wallet, WalletNode node) throws Exception {
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        transaction.addOutput(10000L, wallet.getAddress(node));
        server.serveTransaction(transaction);
        server.serveProof(transaction, PROVEN_HEIGHT, 0);

        wallet.updateTransactions(Map.of(transaction.getTxId(), new BlockTransaction(transaction.getTxId(), PROVEN_HEIGHT, null, 0L, transaction,
                chain.get(PROVEN_HEIGHT - 1).getHash())));
        node.getTransactionOutputs().add(new BlockTransactionHashIndex(transaction.getTxId(), PROVEN_HEIGHT, null, 0L, 0, 10000));

        ScriptHashTx confirmed = new ScriptHashTx(PROVEN_HEIGHT, transaction.getTxId().toString(), 0);
        server.serveHistory(node.getDerivationPath(), confirmed);
        server.serveScriptHashStatus(ElectrumServer.getScriptHash(node), ElectrumServer.getScriptHashStatus(List.of(confirmed)));

        return transaction;
    }

    /**
     * Demotion leaves the retrieved script hash statuses alone, since clearing or withholding one wipes the node's outputs or trips a full history
     * change. What brings the demoted node back is the comparison against its own calculated status, which the fresh subscribe branch has to make as
     * well as the already subscribed one, or a reconnect leaves the transaction unconfirmed until its status changes.
     */
    @Test
    public void refetchesADemotedNodeWhenSubscribingAfresh() throws Exception {
        Wallet wallet = testWallet();
        List<WalletNode> nodes = new ArrayList<>(wallet.getNode(KeyPurpose.RECEIVE).getChildren());
        WalletNode synced = nodes.get(0);
        WalletNode demoted = nodes.get(1);
        Sha256Hash txid = blockTransactions.getFirst().getTxId();
        synced.getTransactionOutputs().add(new BlockTransactionHashIndex(txid, PROVEN_HEIGHT, null, 0L, 0, 10000));
        demoted.getTransactionOutputs().add(new BlockTransactionHashIndex(txid, 0, null, 0L, 0, 10000));

        //A status is a digest of the transaction heights alone, so what the synced node calculates is what the server reports for both of them
        String confirmedStatus = ElectrumServer.getScriptHashStatus(ElectrumServer.getScriptHash(synced), synced);
        for(WalletNode node : List.of(synced, demoted)) {
            String scriptHash = ElectrumServer.getScriptHash(node);
            server.serveScriptHashStatus(scriptHash, confirmedStatus);
            //The put after a demotion runs unchanged, so the demoted node's retrieved status is the server's like any other
            ElectrumServer.retrievedScriptHashes.put(scriptHash, confirmedStatus);
        }

        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new LinkedHashMap<>();
        new ElectrumServer().subscribeWalletNodes(wallet, List.of(synced, demoted), nodeTransactionMap, 0);

        assertEquals(Set.of(demoted), nodeTransactionMap.keySet());
        assertEquals(1, demoted.getTransactionOutputs().size());     //cache state only: the node's own outputs are untouched
    }

    private static Map<WalletNode, Set<BlockTransactionHash>> references(Wallet wallet, BlockTransactionHash... references) {
        WalletNode node = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new LinkedHashMap<>();
        nodeTransactionMap.put(node, new TreeSet<>(List.of(references)));

        return nodeTransactionMap;
    }

    private static BlockTransactionHash reference(Transaction transaction, int height) {
        return new BlockTransaction(transaction.getTxId(), height, null, 0L, null);
    }

    private static void seedStore(List<BlockHeader> chain, int toHeight) throws Exception {
        HeaderStore store = ElectrumServer.getHeaderStore();
        for(int height = 1; height <= toHeight; height++) {
            store.append(chain.get(height - 1));
        }
    }

    /**
     * A chain whose block at PROVEN_HEIGHT carries the given merkle root, the rest being empty blocks that only have to link.
     */
    private static List<BlockHeader> mineChain(BlockHeader previous, int count, Sha256Hash merkleRoot) {
        List<BlockHeader> chain = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            previous = mineHeader(previous, i + 1 == PROVEN_HEIGHT ? merkleRoot : Sha256Hash.ZERO_HASH, CHAIN_TIME + i);
            chain.add(previous);
        }

        return chain;
    }

    private static BlockHeader mineHeader(BlockHeader previous, Sha256Hash merkleRoot, long time) {
        for(long nonce = 0; nonce < 1000; nonce++) {
            BlockHeader header = new BlockHeader(1, previous.getHash(), merkleRoot, null, time, 0x207fffffL, nonce);
            if(header.verifyProofOfWork()) {
                return header;
            }
        }

        throw new IllegalStateException("Could not mine a regtest header at time " + time);
    }

    private static Transaction transaction(int index) {
        Transaction transaction = new Transaction();
        transaction.addInput(Sha256Hash.ZERO_HASH, index, new Script(new byte[0]));
        transaction.addOutput(10000L + index, new Script(new byte[] {0x51}));

        return transaction;
    }

    private static List<Sha256Hash> txids(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getTxId).toList();
    }

    private static Sha256Hash merkleRoot(List<Sha256Hash> txids) {
        List<Sha256Hash> level = new ArrayList<>(txids);
        while(level.size() > 1) {
            level = nextLevel(level);
        }

        return level.getFirst();
    }

    /**
     * The sibling path from the leaf at the given position to the root, deepest level first, which is the shape the server returns.
     */
    private static List<String> merkleBranch(List<Sha256Hash> txids, int position) {
        List<String> branch = new ArrayList<>();
        List<Sha256Hash> level = new ArrayList<>(txids);
        int index = position;
        while(level.size() > 1) {
            List<Sha256Hash> padded = new ArrayList<>(level);
            if(padded.size() % 2 == 1) {
                padded.add(padded.getLast());
            }
            branch.add(padded.get(index ^ 1).toString());
            level = nextLevel(level);
            index >>= 1;
        }

        return branch;
    }

    private static List<Sha256Hash> nextLevel(List<Sha256Hash> level) {
        List<Sha256Hash> padded = new ArrayList<>(level);
        if(padded.size() % 2 == 1) {
            padded.add(padded.getLast());
        }

        List<Sha256Hash> next = new ArrayList<>();
        for(int i = 0; i < padded.size(); i += 2) {
            next.add(Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Utils.concat(padded.get(i).getReversedBytes(), padded.get(i + 1).getReversedBytes()))));
        }

        return next;
    }

    /**
     * A recent mempool transaction confirming yields the pair a wallet transaction confirming would, so the request that follows is the same one and
     * the traffic after a new block is the same shape whether or not the wallet has anything in it. The height comes from the status guess.
     */
    @Test
    public void provesARecentTransactionAsItConfirms() {
        Transaction transaction = blockTransactions.getFirst();
        AppServices.setAnnouncedTip(new ChainTip(PROVEN_HEIGHT, chain.get(PROVEN_HEIGHT)));

        String scriptHash = "aa".repeat(32);
        ElectrumServer.confirmingRecent.put(scriptHash, new BlockTransaction(transaction.getTxId(), 0, null, RECENT_FEE, transaction));

        String confirmedStatus = ElectrumServer.getScriptHashStatus(List.of(new ScriptHashTx(PROVEN_HEIGHT, transaction.getTxId().toString(), RECENT_FEE)));
        BlockTransactionHash reference = ElectrumServer.ConnectionService.getProofReference(new WalletNodeHistoryChangedEvent(scriptHash, confirmedStatus));

        assertNotNull(reference);
        assertEquals(transaction.getTxId(), reference.getHash());
        assertEquals(PROVEN_HEIGHT, reference.getHeight());
        //Consumed, so a further notification on the same script hash does not ask a second time
        assertFalse(ElectrumServer.confirmingRecent.containsKey(scriptHash));
    }

    /**
     * The guess is the entire height mechanism here, so a status it does not explain contributes nothing for that block. There is deliberately no
     * fallback: asking the server for the history is the one call a wallet node whose history is already known does not make.
     */
    @Test
    public void makesNoRequestWhereTheRecentTransactionHasNotConfirmed() {
        Transaction transaction = blockTransactions.getFirst();
        AppServices.setAnnouncedTip(new ChainTip(PROVEN_HEIGHT, chain.get(PROVEN_HEIGHT)));

        String scriptHash = "bb".repeat(32);
        ElectrumServer.confirmingRecent.put(scriptHash, new BlockTransaction(transaction.getTxId(), 0, null, RECENT_FEE, transaction));

        //The status the script hash carries while its transaction is still in the mempool
        String unconfirmedStatus = ElectrumServer.getScriptHashStatus(List.of(new ScriptHashTx(0, transaction.getTxId().toString(), RECENT_FEE)));
        assertNull(ElectrumServer.ConnectionService.getProofReference(new WalletNodeHistoryChangedEvent(scriptHash, unconfirmedStatus)));

        //Left in place, so the notification for its confirmation is still matched
        assertTrue(ElectrumServer.confirmingRecent.containsKey(scriptHash));
    }

    /**
     * Verification being off leaves the wallet making no proof requests at all, so making them for anything else would be traffic with nothing to
     * cover. The surrounding gates already exclude Bitcoin Core and Tor by never reaching this code.
     */
    @Test
    public void makesNoRequestWhereTransactionsAreNotBeingVerified() {
        Transaction transaction = blockTransactions.getFirst();
        AppServices.setAnnouncedTip(new ChainTip(PROVEN_HEIGHT, chain.get(PROVEN_HEIGHT)));

        String scriptHash = "cc".repeat(32);
        ElectrumServer.confirmingRecent.put(scriptHash, new BlockTransaction(transaction.getTxId(), 0, null, RECENT_FEE, transaction));
        String confirmedStatus = ElectrumServer.getScriptHashStatus(List.of(new ScriptHashTx(PROVEN_HEIGHT, transaction.getTxId().toString(), RECENT_FEE)));

        ElectrumServer.serverCapability.withMerkleProofs(false);
        assertFalse(ElectrumServer.isVerifyingTransactions());
        assertNull(ElectrumServer.ConnectionService.getProofReference(new WalletNodeHistoryChangedEvent(scriptHash, confirmedStatus)));
        //Left in place rather than consumed, so turning verification back on within the retention window still covers the block
        assertTrue(ElectrumServer.confirmingRecent.containsKey(scriptHash));
    }

    private static Wallet testWallet() {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        Keystore keystore = new Keystore();
        keystore.setKeyDerivation(new KeyDerivation("00000000", "m/84'/0'/0'"));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(Network.get() == Network.MAINNET ? TEST_XPUB : TEST_TPUB));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), 1));
        wallet.getNode(KeyPurpose.RECEIVE).fillToIndex(wallet, 1);

        return wallet;
    }

    /**
     * Answers proofs, headers and transactions from what the test has served it, recording what it was asked for. A pair the test has not served a
     * proof for is answered with the error sentinel every attempt, which is a server that will not substantiate what it reported.
     */
    private class FakeProofServer extends SimpleElectrumServerRpc {
        private final List<BlockHeader> chain;
        private final Map<String, TransactionMerkleProof> proofs = new HashMap<>();
        private final Map<String, Integer> refusals = new HashMap<>();
        private final Map<String, String> rawTransactions = new HashMap<>();
        private final Map<String, String> scriptHashStatuses = new HashMap<>();
        private final Map<String, ScriptHashTx[]> histories = new HashMap<>();
        private volatile Integer invalidateAtForkHeight;
        private final AtomicInteger proofRequests = new AtomicInteger();
        private final AtomicInteger blockHeaderRequests = new AtomicInteger();
        private final List<Set<String>> proofRequestKeys = new ArrayList<>();
        private volatile int servedChainLength;
        private volatile RuntimeException proofFailure;
        private volatile int proofFailureAfter;
        private volatile int proofFailureUntil = Integer.MAX_VALUE;
        private volatile RuntimeException headersFailure;

        public FakeProofServer(List<BlockHeader> chain) {
            this.chain = List.copyOf(chain);
            this.servedChainLength = chain.size();
        }

        @Override
        public Map<String, TransactionMerkleProof> getTransactionMerkleProofs(Transport transport, Wallet wallet, Collection<BlockTransactionHash> references) {
            int request = proofRequests.incrementAndGet();
            proofRequestKeys.add(references.stream().map(FakeProofServer::key).collect(Collectors.toCollection(LinkedHashSet::new)));
            if(proofFailure != null && request > proofFailureAfter && request <= proofFailureUntil) {
                throw proofFailure;
            }

            Map<String, TransactionMerkleProof> result = new LinkedHashMap<>();
            for(BlockTransactionHash reference : references) {
                String key = key(reference);
                Integer remaining = refusals.get(key);
                if(remaining != null && remaining > 0) {
                    refusals.put(key, remaining - 1);
                    result.put(key, TransactionMerkleProof.ERROR_PROOF);
                } else {
                    result.put(key, proofs.getOrDefault(key, TransactionMerkleProof.ERROR_PROOF));
                }
            }

            return result;
        }

        @Override
        public BlockHeaders getBlockHeadersChunk(Transport transport, int startHeight, int count) {
            if(headersFailure != null) {
                throw headersFailure;
            }

            int index = startHeight - 1;
            int available = Math.max(0, Math.min(count, servedChainLength - index));
            List<BlockHeader> headers = available == 0 ? Collections.emptyList() : chain.subList(index, index + available);
            BlockHeaders blockHeaders = new BlockHeaders();
            blockHeaders.count = headers.size();
            blockHeaders.max = HeaderChainState.RETARGET_INTERVAL;
            blockHeaders.hex = headers.stream().map(header -> Utils.bytesToHex(header.bitcoinSerialize())).collect(Collectors.joining());

            return ElectrumServerRpc.checkBlockHeaders(blockHeaders, startHeight, count, servedChainLength);
        }

        @Override
        public Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights) {
            blockHeaderRequests.incrementAndGet();
            Map<Integer, String> result = new TreeMap<>();
            for(Integer height : blockHeights) {
                if(height >= 1 && height <= chain.size()) {
                    result.put(height, Utils.bytesToHex(chain.get(height - 1).bitcoinSerialize()));
                }
            }

            return result;
        }

        @Override
        public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
            Map<String, String> result = new LinkedHashMap<>();
            for(String txid : txids) {
                String hex = rawTransactions.get(txid);
                result.put(txid, hex == null ? Sha256Hash.ZERO_HASH.toString() : hex);
            }

            return result;
        }

        @Override
        public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
            Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
            pathScriptHashes.keySet().forEach(path -> result.put(path, histories.getOrDefault(path, new ScriptHashTx[0])));

            return result;
        }

        @Override
        public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
            //Stands in for a reorg reconciled while this pass is in flight, whether by the sync service or by a history thread's own gate. The real
            //invalidation is used rather than an imitation of it, since what it clears is half of what the pass must not put back
            if(invalidateAtForkHeight != null) {
                ElectrumServer.invalidateScriptHashesForReorg(wallet, invalidateAtForkHeight);
                invalidateAtForkHeight = null;
            }

            Map<String, String> result = new LinkedHashMap<>();
            pathScriptHashes.forEach((path, scriptHash) -> result.put(path, scriptHashStatuses.get(scriptHash)));

            return result;
        }

        public void invalidateDuringPass(int forkHeight) {
            this.invalidateAtForkHeight = forkHeight;
        }

        public void serveHistory(String derivationPath, ScriptHashTx... history) {
            histories.put(derivationPath, history);
        }

        public void serveScriptHashStatus(String scriptHash, String status) {
            scriptHashStatuses.put(scriptHash, status);
        }

        public void serveTransaction(Transaction transaction) {
            rawTransactions.put(transaction.getTxId().toString(), Utils.bytesToHex(transaction.bitcoinSerialize()));
        }

        public TransactionMerkleProof serveProof(Transaction transaction, int height, int position) {
            TransactionMerkleProof proof = new TransactionMerkleProof();
            proof.block_height = height;
            proof.pos = position;
            proof.merkle = merkleBranch(txids(blockTransactions), position);
            proofs.put(transaction.getTxId() + ":" + height, proof);

            return proof;
        }

        public void serveProof(Sha256Hash txid, int height, TransactionMerkleProof proof) {
            proofs.put(txid + ":" + height, proof);
        }

        public void refuseFirstAttempts(Transaction transaction, int height, int attempts) {
            refusals.put(transaction.getTxId() + ":" + height, attempts);
        }

        public void setProofFailure(RuntimeException proofFailure) {
            this.proofFailure = proofFailure;
        }

        /**
         * Answers the given number of proof requests before failing every one after them, which is a connection lost partway through a verification.
         */
        public void failAfterRequests(int requests, RuntimeException failure) {
            this.proofFailureAfter = requests;
            this.proofFailure = failure;
        }

        /**
         * Fails the given number of proof requests before answering the rest, which is a server momentarily unable to answer the call at all.
         */
        public void failFirstRequests(int requests, RuntimeException failure) {
            this.proofFailureUntil = requests;
            this.proofFailure = failure;
        }

        public void setHeadersFailure(RuntimeException headersFailure) {
            this.headersFailure = headersFailure;
        }

        public void setServedChainLength(int servedChainLength) {
            this.servedChainLength = servedChainLength;
        }

        public int getProofRequests() {
            return proofRequests.get();
        }

        public int getBlockHeaderRequests() {
            return blockHeaderRequests.get();
        }

        public List<Set<String>> getProofRequestKeys() {
            return proofRequestKeys;
        }

        private static String key(BlockTransactionHash reference) {
            return reference.getHashAsString() + ":" + reference.getHeight();
        }
    }

    /**
     * The events are dispatched on the thread that verified, so they are captured by the time the call returns.
     */
    public static class ProofListener {
        private final Set<BlockTransactionHash> failed = new LinkedHashSet<>();
        private final Set<BlockTransactionHash> refused = new LinkedHashSet<>();
        private int failedEvents;
        private int refusedEvents;

        @Subscribe
        public void transactionProofsFailed(TransactionProofsFailedEvent event) {
            failedEvents++;
            failed.addAll(event.getReferences());
        }

        @Subscribe
        public void transactionProofsRefused(TransactionProofsRefusedEvent event) {
            refusedEvents++;
            refused.addAll(event.getReferences());
        }

        public Set<BlockTransactionHash> getFailed() {
            return failed;
        }

        public Set<BlockTransactionHash> getRefused() {
            return refused;
        }

        public int getFailedEvents() {
            return failedEvents;
        }

        public int getRefusedEvents() {
            return refusedEvents;
        }

        public boolean isEmpty() {
            return failedEvents == 0 && refusedEvents == 0;
        }

        public void reset() {
            failed.clear();
            refused.clear();
            failedEvents = 0;
            refusedEvents = 0;
        }
    }

    /**
     * A transport that reports itself connected without opening a socket, since the fake answers without it.
     */
    private static class UnusedTransport extends TcpTransport {
        private final boolean connected;

        public UnusedTransport() {
            this(true);
        }

        public UnusedTransport(boolean connected) {
            super(HostAndPort.fromParts("localhost", 1));
            this.connected = connected;
        }

        @Override
        public String pass(String request) {
            throw new UnsupportedOperationException("The fake server answers without the transport");
        }

        @Override
        public boolean isConnected() {
            return connected;
        }
    }
}
