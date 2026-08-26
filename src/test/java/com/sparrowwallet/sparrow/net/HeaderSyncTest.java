package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.ChainReorgEvent;
import com.sparrowwallet.sparrow.io.Storage;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The header sync orchestration against a server that answers from a chain of the test's choosing: which divergences are detected and where, which
 * candidates are adopted, and how often the server is asked for anything. The failure these cover is not a lost header but a false accusation - a
 * store left on an orphaned header reconstructs no merkle branch, and every proof against it is reported as a dishonest server.
 * <p>
 * Regtest is the only network whose proof of work target a synthetic chain can be mined against, and its empty checkpoints anchor the store at the
 * genesis header, so every height here is one the store itself serves.
 */
public class HeaderSyncTest {
    //Mainnet heights 32248 to 32255, the close of difficulty period 15, whose last header is the compiled in pin at height 32255
    private static final List<BlockHeader> PERIOD_15_CLOSE = Stream.of(
            "0100000062f481d3ac76c0464800c12b32a724aa05b85aefb735f104407c7643000000004b7a6e15f0331c1619d739d5ddbfedc10208b09c7e44543a7f8450a6cb13049d57e03a4bffff001deada8502",
            "01000000de07b0f62ba82662b23f920a8429f019731043affa17819e4b23dcd4000000009bed3868aee66f1babd0aced30ea3c4272265e9032758b65b6f9ff50960ff384b6e03a4bffff001da8beb001",
            "01000000bfeeabd547eb6fc8fa4152725030d8ca0e03ce03f912700601fa3ad2000000000cfc1c060eea439947fb50b9f082ef626f66608cd32b2c93604e8c05caabda96f1e13a4bffff001d5a661f02",
            "0100000019179cf0fe8ef7d751e007c11dd8097d77d52d126f1afea9c7f83129000000003c5e7fd03948b4e413c69bcbf6208d407910d9d3e0bdde65c572d46e14b526f146e23a4bffff001d9e369a01",
            "01000000a90a01b124aa4ab2a31923511e138939a4053a5f3f2be7186e820d3600000000fb6e3ed118edc6171a2e50a8484ba7c33fe6bd6efb49e90b8b7d0b0ea5e800b862e23a4bffff001d6db69d00",
            "010000002d5d7f4a4dad16f92e4b59d1903f34cc6acf40254f64718f32b25e3d00000000c2a630dbfefd5a55a939d39388d1daad1ff2ca22de59ddfd1464b494e61acb0410e93a4bffff001de3c38c1e",
            "01000000672ae405fdb9e4f2a37ffa660a328e138fa08a9ac7ae99382aee270e00000000342fedae2d72975552ac0797556817d11006ec322e2bf97ce88f91caf39525794fe93a4bffff001d94282401",
            "0100000049c1daab3b6536ff1b2633c3a316a6e06ec287676cdeec4ca7baae6b00000000ac10b36b8f354b3353207de15940a5edbc05bb8364af75b4b5409e7823f2b48923ec3a4bffff001dbd5fa412")
            .map(hex -> new BlockHeader(Utils.hexToBytes(hex))).toList();

    private static final long CHAIN_TIME = 1600000000L;
    private static final long BRANCH_TIME = 1700000000L;

    @TempDir
    private static Path tempHome;

    private ElectrumServerRpc previousElectrumServerRpc;
    private CloseableTransport previousTransport;
    private ChainReorgListener listener;

    @BeforeAll
    public static void setUpAll() {
        //Config.get() caches its instance statically for the life of the JVM, so keep this test from loading the developer's real config
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() {
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
        ElectrumServer.retrievedBlockHeaders.clear();
        previousElectrumServerRpc = ElectrumServer.electrumServerRpc;
        previousTransport = ElectrumServer.transport;
        //The fake answers without the transport, but getTransport() would otherwise build one from the configured server
        ElectrumServer.transport = new UnusedTransport();
        listener = new ChainReorgListener();
        EventManager.get().register(listener);
    }

    @AfterEach
    public void tearDown() {
        EventManager.get().unregister(listener);
        ElectrumServer.electrumServerRpc = previousElectrumServerRpc;
        ElectrumServer.transport = previousTransport;
        ElectrumServer.headerStore = null;
        ElectrumServer.lastReorgForkHeight = Integer.MAX_VALUE;
        ElectrumServer.verifiedHistoricalHeaders.clear();
        ElectrumServer.retrievedBlockHeaders.clear();
        AppServices.setAnnouncedTip(null);
        Network.set(null);
    }

    /**
     * The cache of headers the server has announced as its tip is not the store, and nothing else rewinds it: above the fork it names blocks the chain
     * no longer has, and only the current tip is replaced by the next announcement. Left there it would serve the replaced block's timestamp at any
     * height that was once a tip, which is the height a transaction re-proven against its replacement is looked up at.
     */
    @Test
    public void dropsAnnouncedHeadersAboveTheForkOnReorganising() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 1);
        seedStore(chain, 10);
        serve(branch);
        ElectrumServer.updateRetrievedBlockHeaders(8, chain.get(7));
        ElectrumServer.updateRetrievedBlockHeaders(9, chain.get(8));
        ElectrumServer.updateRetrievedBlockHeaders(10, chain.get(9));

        new ElectrumServer().syncHeaders(new ChainTip(10, branch.getLast()));

        assertEquals(9, listener.getForkHeight());
        assertEquals(chain.get(7), ElectrumServer.retrievedBlockHeaders.get(8));      //below the fork, still the chain the store keeps
        assertEquals(chain.get(8), ElectrumServer.retrievedBlockHeaders.get(9));
        assertNull(ElectrumServer.retrievedBlockHeaders.get(10));                     //the replaced block, dropped with the header it came from
    }

    /**
     * The tie: a stale block replaced at the store tip height. Equal work is accepted, because a client with one server can only verify against the
     * chain that server serves - and because the loop that fetches headers would never look at a tip that is not above its own.
     */
    @Test
    public void adoptsAReplacementTipOfEqualWork() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 1);
        HeaderStore store = seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(branch);

        new ElectrumServer().syncHeaders(new ChainTip(10, branch.getLast()));

        assertEquals(10, store.getTipHeight());
        assertEquals(branch.getLast().getHash(), store.getTipHash());
        assertEquals(9, ElectrumServer.lastReorgForkHeight);
        assertEquals(9, listener.getForkHeight());
        assertEquals(1, fake.getChunkRequests());
    }

    @Test
    public void refusesACandidateCarryingLessWork() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 5, 4);
        HeaderStore store = seedStore(chain, 10);
        serve(branch);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(9, branch.getLast())));
        assertTrue(e.getMessage().contains("less work"), e.getMessage());

        //The store keeps the heavier chain it already has, and nothing is recorded as reorganised
        assertEquals(10, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertEquals(chain.get(5).getHash(), store.getHeader(6).getHash());
        assertEquals(Integer.MAX_VALUE, ElectrumServer.lastReorgForkHeight);
        assertNull(listener.getForkHeight());
    }

    /**
     * A divergence deeper than the reorg window cannot be linked to anything the store holds, and a chain that deep is a global event rather than a
     * client concern: the store keeps what it has and the heights in dispute are refused until the server catches up.
     */
    @Test
    public void refusesACandidateWithNoForkPointWithinTheReorgWindow() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 150, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 20, 130);
        HeaderStore store = seedStore(chain, 150);
        serve(branch);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(150, branch.getLast())));
        assertTrue(e.getMessage().contains("shares no fork point"), e.getMessage());

        assertEquals(150, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertEquals(Integer.MAX_VALUE, ElectrumServer.lastReorgForkHeight);
        assertNull(listener.getForkHeight());
    }

    /**
     * A store tip that is off the server's chain while the server has moved far ahead of it. The fork point is at or below the store tip whatever the
     * server has announced, so the search window has to sit at the store tip: anchoring it at the announced tip searches a range the store holds no
     * height in, and the sync then refuses every height for good, with the file needing deletion by hand.
     */
    @Test
    public void reconcilesAtTheStoreTipWhenTheServerIsFarAhead() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 151);
        HeaderStore store = seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(branch);

        new ElectrumServer().syncHeaders(new ChainTip(160, branch.getLast()));

        assertEquals(160, store.getTipHeight());
        assertEquals(branch.getLast().getHash(), store.getTipHash());
        assertEquals(branch.get(9).getHash(), store.getHeader(10).getHash());
        assertEquals(9, ElectrumServer.lastReorgForkHeight);
        assertEquals(9, listener.getForkHeight());
        //The chunk that found the divergence, the window that found the fork, and the forward chunk from the reconciled tip
        assertEquals(3, fake.getChunkRequests());
    }

    /**
     * The fork walk is what identifies the point to rewind to, so a window that does not chain to itself cannot be mined for one: the walk stops at the
     * break rather than continuing past it to whatever lies below.
     */
    @Test
    public void refusesAWindowThatDoesNotChainToItself() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> candidate = new ArrayList<>(chain.subList(0, 8));
        BlockHeader unlinked = mineHeader(mineHeader(Network.REGTEST.getGenesisHeader(), BRANCH_TIME), BRANCH_TIME);
        candidate.add(unlinked);
        candidate.addAll(mineChain(unlinked, 1, BRANCH_TIME + 1));
        HeaderStore store = seedStore(chain, 10);
        serve(candidate);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(10, candidate.getLast())));
        assertTrue(e.getMessage().contains("shares no fork point"), e.getMessage());

        assertEquals(10, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertNull(listener.getForkHeight());
    }

    /**
     * Finding a fork point is not enough to be adopted: every header above it is put through the chain state, so a segment carrying one that does not
     * meet its target is refused whole and the store keeps what it has.
     */
    @Test
    public void refusesASegmentCarryingAnInvalidHeader() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> candidate = new ArrayList<>(chain.subList(0, 5));
        candidate.addAll(mineChain(chain.get(4), 1, BRANCH_TIME));
        BlockHeader belowTarget = unmineHeader(candidate.getLast(), BRANCH_TIME + 1);
        candidate.add(belowTarget);
        candidate.addAll(mineChain(belowTarget, 3, BRANCH_TIME + 2));
        HeaderStore store = seedStore(chain, 10);
        serve(candidate);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(10, candidate.getLast())));
        assertTrue(e.getMessage().contains("proof of work"), e.getMessage());

        assertEquals(10, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertEquals(chain.get(5).getHash(), store.getHeader(6).getHash());
        assertEquals(Integer.MAX_VALUE, ElectrumServer.lastReorgForkHeight);
        assertNull(listener.getForkHeight());
    }

    /**
     * The extension: a server on a fork announcing a tip above the store. The divergence is found by the prev hash check on the first header of the
     * fetched chunk, on the thread that needed the height - a wallet history thread here, not the sync service - and the reorg runs there.
     */
    @Test
    public void detectsAForkFromTheFirstHeaderOfAFetchedChunk() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 5, 7);
        HeaderStore store = seedStore(chain, 10);
        serve(branch);
        AppServices.setAnnouncedTip(new ChainTip(12, branch.getLast()));

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(11);

        assertEquals(branch.get(10).getHash(), verified.getHash());
        assertEquals(11, store.getTipHeight());
        assertEquals(branch.get(5).getHash(), store.getHeader(6).getHash());
        assertEquals(5, ElectrumServer.lastReorgForkHeight);
        assertEquals(5, listener.getForkHeight());
    }

    /**
     * The same far ahead divergence reached the other way, by a wallet history thread asking for a height the sync service has not reached. The height
     * it needs is no more related to where the fork lies than the announced tip is, so the window has to be anchored at the store tip here too.
     */
    @Test
    public void reconcilesAtTheStoreTipWhenAHistoryThreadNeedsAFarHeight() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 151);
        HeaderStore store = seedStore(chain, 10);
        serve(branch);
        AppServices.setAnnouncedTip(new ChainTip(160, branch.getLast()));

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(150);

        assertEquals(branch.get(149).getHash(), verified.getHash());
        assertEquals(160, store.getTipHeight());
        assertEquals(branch.get(9).getHash(), store.getHeader(10).getHash());
        assertEquals(9, ElectrumServer.lastReorgForkHeight);
        assertEquals(9, listener.getForkHeight());
    }

    /**
     * The edge of the reorg window, where the work comparison is at its limit: a fork exactly MAX_REORG_DEPTH - 1 below the store tip is found and the
     * candidate that replaces those headers is adopted, while one header deeper is out of reach and refused with the store untouched.
     */
    @Test
    public void reorganisesAtTheDeepestReachableForkAndRefusesOneDeeper() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 200, CHAIN_TIME);

        //The store holds 99 headers above the fork, and the candidate 100: the window starts at the header immediately above the fork
        List<BlockHeader> reachable = fork(chain, 101, 100);
        HeaderStore store = seedStore(chain, 200);
        serve(reachable);
        new ElectrumServer().syncHeaders(new ChainTip(201, reachable.getLast()));

        assertEquals(201, store.getTipHeight());
        assertEquals(reachable.getLast().getHash(), store.getTipHash());
        assertEquals(reachable.get(101).getHash(), store.getHeader(102).getHash());
        assertEquals(101, ElectrumServer.lastReorgForkHeight);
        assertEquals(101, listener.getForkHeight());

        //One header deeper the fork falls outside the window, and nothing of it can be linked to what the store holds
        ElectrumServer.headerStore = null;
        ElectrumServer.lastReorgForkHeight = Integer.MAX_VALUE;
        for(File file : Storage.getHeadersDir().listFiles()) {
            assertTrue(file.delete());
        }

        List<BlockHeader> unreachable = fork(chain, 100, 101);
        HeaderStore reseeded = seedStore(chain, 200);
        serve(unreachable);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(201, unreachable.getLast())));
        assertTrue(e.getMessage().contains("shares no fork point"), e.getMessage());
        assertEquals(200, reseeded.getTipHeight());
        assertEquals(chain.getLast().getHash(), reseeded.getTipHash());
        assertEquals(Integer.MAX_VALUE, ElectrumServer.lastReorgForkHeight);
    }

    /**
     * The steady state, on both paths that sync: a header that extends the store tip is appended from the announcement itself, so a new block costs
     * no request at all. The service passes the pair from its event; a history thread reads the same pair from the announced tip.
     */
    @Test
    public void appendsAnAnnouncedHeaderWithoutFetching() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        HeaderStore store = seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);

        new ElectrumServer().syncHeaders(new ChainTip(11, chain.get(10)));
        assertEquals(11, store.getTipHeight());
        assertEquals(chain.get(10).getHash(), store.getTipHash());
        assertEquals(0, fake.getChunkRequests());

        AppServices.setAnnouncedTip(new ChainTip(12, chain.get(11)));
        BlockHeader verified = new ElectrumServer().getVerifiedHeader(12);
        assertEquals(chain.get(11).getHash(), verified.getHash());
        assertEquals(12, store.getTipHeight());
        assertEquals(0, fake.getChunkRequests());
    }

    /**
     * The announced header is appended only where it descends from the store tip: the height it claims is a pre-check, and taking it on that alone
     * would put a header of another chain through the chain state and turn an ordinary one block reorg into a refusal.
     */
    @Test
    public void doesNotAppendAnAnnouncedHeaderThatDoesNotDescendFromTheStoreTip() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 2);
        HeaderStore store = seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(branch);

        //The announcement is at the height that extends the store, but on the chain that replaced its tip
        new ElectrumServer().syncHeaders(new ChainTip(11, branch.getLast()));

        assertEquals(11, store.getTipHeight());
        assertEquals(branch.getLast().getHash(), store.getTipHash());
        assertEquals(branch.get(9).getHash(), store.getHeader(10).getHash());
        assertEquals(9, listener.getForkHeight());
        //The chunk that exposed the divergence, then the window that resolved it
        assertEquals(2, fake.getChunkRequests());
    }

    /**
     * An announced header is no more trusted than a fetched one. The tip validation it has already passed only checks it against the target it claims
     * for itself, so what the chain requires of it at that height is enforced here, where it is written.
     */
    @Test
    public void refusesAnAnnouncedHeaderThatDoesNotMeetItsTarget() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        HeaderStore store = seedStore(chain, 10);
        serve(chain);
        BlockHeader belowTarget = unmineHeader(chain.getLast(), CHAIN_TIME + 10);

        VerificationException e = assertThrows(VerificationException.class, () -> new ElectrumServer().syncHeaders(new ChainTip(11, belowTarget)));
        assertTrue(e.getMessage().contains("proof of work"), e.getMessage());

        assertEquals(10, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
    }

    /**
     * Two threads syncing an empty store at once, which is the ordinary case on a cold install: the history threads of the open wallets and the sync
     * service after its startup jitter. Without the lock both fetch the same chunk and the loser's append fails linkage, which reads as an invalid
     * segment from an honest server.
     */
    @Test
    public void fetchesEachChunkOnceWhenTwoThreadsRace() throws Exception {
        int height = HeaderChainState.RETARGET_INTERVAL + 484;
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), height, CHAIN_TIME);
        HeaderStore store = seedStore(chain, 0);
        FakeElectrumServerRpc fake = serve(chain);
        fake.setResponseDelayMillis(50);

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Void> sync = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            new ElectrumServer().syncHeadersTo(height);
            return null;
        };

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            for(Future<Void> future : executorService.invokeAll(List.of(sync, sync))) {
                future.get(60, TimeUnit.SECONDS);   //rethrows a VerificationException from either thread
            }
        } finally {
            executorService.shutdownNow();
        }

        assertEquals(height, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        //One request per chunk: the thread that waited re-read the tip inside the loop and found nothing left to fetch
        assertEquals(2, fake.getChunkRequests());
    }

    /**
     * A stored header is never served while the announced tip disagrees with the store at that height. Serving the orphaned header instead is what
     * turns a natural stale block into the dishonest server dialog: the proof for a transaction in the replacing block reconstructs no branch.
     */
    @Test
    public void reconcilesBeforeServingAStoredHeaderTheAnnouncedTipContradicts() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        List<BlockHeader> branch = fork(chain, 9, 1);
        HeaderStore store = seedStore(chain, 10);
        serve(branch);
        AppServices.setAnnouncedTip(new ChainTip(10, branch.getLast()));

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(10);

        assertEquals(branch.getLast().getHash(), verified.getHash());
        assertEquals(branch.getLast().getHash(), store.getTipHash());
        assertEquals(9, listener.getForkHeight());
    }

    @Test
    public void servesAStoredHeaderWithoutFetchingWhileTheAnnouncedTipAgrees() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10, CHAIN_TIME);
        seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);
        AppServices.setAnnouncedTip(new ChainTip(10, chain.getLast()));

        //An in sync store never queues a proof behind a request of any kind
        assertEquals(chain.get(7).getHash(), new ElectrumServer().getVerifiedHeader(8).getHash());
        assertEquals(chain.get(9).getHash(), new ElectrumServer().getVerifiedHeader(10).getHash());
        assertEquals(0, fake.getChunkRequests());
    }

    /**
     * A chunk claiming more headers than it carries is a refusal like any other malformed response, not an exception escaping the sync. The response
     * checks in the rpc layer already reject this shape, so what is pinned here is that the loop reading the chunk does not depend on them having run.
     */
    @Test
    public void treatsAChunkShorterThanItsCountAsARefusal() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);

        BlockHeaders malformed = new BlockHeaders();
        malformed.count = 3;
        malformed.max = HeaderChainState.RETARGET_INTERVAL;
        malformed.hex = Utils.bytesToHex(chain.get(10).bitcoinSerialize());      //one header where three are claimed
        fake.setMalformedResponse(malformed);

        assertNull(new ElectrumServer().getVerifiedHeader(12));
    }

    /**
     * The loaded store outlives the connection, so a file removed underneath it - by a user clearing the cache, or by another instance - has to be
     * noticed. Every read of a file that is gone comes back empty, which the fork walk would otherwise report as a server sharing no fork point with
     * a chain it served itself.
     */
    @Test
    public void reloadsAStoreWhoseFileHasBeenRemoved() throws Exception {
        //Further above the anchor than the reorg window reaches, as any real store is: the walk cannot fall back on the pinned hash to find a fork
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 150, CHAIN_TIME);
        seedStore(chain, 150);
        serve(chain);

        for(File file : Storage.getHeadersDir().listFiles()) {
            assertTrue(file.delete());
        }

        new ElectrumServer().syncHeaders(new ChainTip(150, chain.getLast()));

        HeaderStore reloaded = ElectrumServer.getHeaderStore();
        assertEquals(150, reloaded.getTipHeight());
        assertEquals(chain.getLast().getHash(), reloaded.getTipHash());
    }

    /**
     * A transport failure is not a refusal: the server has said nothing about these heights, so the wallet history fails as it does for any other
     * failed call, rather than the height being reported as one the server could not substantiate.
     */
    @Test
    public void reportsATransportFailureAsAServerException() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);
        fake.setFailure(new ElectrumServerRpcException("Connection reset"));

        assertThrows(ServerException.class, () -> new ElectrumServer().getVerifiedHeader(12));
    }

    /**
     * A server without the call is a property of the server rather than of the height, and the caller acts on it by disabling verification for the
     * session, so it must reach that caller as itself rather than as a refusal or a wrapped failure.
     */
    @Test
    public void letsAnUnsupportedMethodReachTheCaller() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);
        fake.setFailure(new UnsupportedMethodException("blockchain.block.headers", new IllegalStateException()));

        assertThrows(UnsupportedMethodException.class, () -> new ElectrumServer().getVerifiedHeader(12));
    }

    /**
     * A height at or below the last pin is verified by hash linkage to it, with no proof of work, difficulty or timestamp check: descent from a hash
     * that is compiled in is what places a header at its height. These are the real mainnet headers closing difficulty period 15, whose last one is
     * the pin at height 32255, so nothing here could be forged to pass.
     */
    @Test
    public void verifiesAHistoricalHeaderByLinkageToItsPin() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(32250);

        assertEquals(PERIOD_15_CLOSE.get(2).getHash(), verified.getHash());
        //Fetched from the height asked for up to its pin, and every height between is now known
        assertEquals(32250, fake.getLastStartHeight());
        assertEquals(6, fake.getLastCount());
        assertEquals(1, fake.getChunkRequests());
        assertEquals(Network.MAINNET.getHeaderCheckpoints().getHash(32255), ElectrumServer.verifiedHistoricalHeaders.get(32255).getHash());

        //A second height inside the range it already holds costs nothing
        assertEquals(PERIOD_15_CLOSE.get(5).getHash(), new ElectrumServer().getVerifiedHeader(32253).getHash());
        assertEquals(1, fake.getChunkRequests());
    }

    /**
     * A request for a pinned height itself, where the range collapses to the single header the pin names and there is nothing to link.
     */
    @Test
    public void verifiesAPinnedHeightFromItsOwnHeaderAlone() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(32255);

        assertEquals(Network.MAINNET.getHeaderCheckpoints().getHash(32255), verified.getHash());
        assertEquals(32255, fake.getLastStartHeight());
        assertEquals(1, fake.getLastCount());
    }

    /**
     * A later pass reaching below a range already verified links to the nearest header it holds rather than to the pin above it, so an already
     * downloaded range is not downloaded again.
     */
    @Test
    public void fetchesOnlyAsFarAsTheNearestVerifiedHeader() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);
        new ElectrumServer().getVerifiedHeader(32250);

        BlockHeader verified = new ElectrumServer().getVerifiedHeader(32248);

        assertEquals(PERIOD_15_CLOSE.getFirst().getHash(), verified.getHash());
        //Anchored on the cached header at 32250 rather than on the pin at 32255
        assertEquals(32248, fake.getLastStartHeight());
        assertEquals(3, fake.getLastCount());
        assertEquals(2, fake.getChunkRequests());
    }

    /**
     * The restore time prefetch: the heights a batch of proofs needs below the last pin are coalesced into one range per difficulty period, from the
     * lowest height needed in it, so that every other height in the period is served from the cache without a request of its own.
     */
    @Test
    public void prefetchesOneRangePerPeriodForTheHeightsBeingProven() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);

        new ElectrumServer().prefetchVerifiedHeaders(List.of(32252, 32249, 32249, 32255));

        assertEquals(1, fake.getChunkRequests());
        assertEquals(32249, fake.getLastStartHeight());
        assertEquals(7, fake.getLastCount());       //from the lowest height needed up to the pin

        assertEquals(PERIOD_15_CLOSE.get(4).getHash(), new ElectrumServer().getVerifiedHeader(32252).getHash());
        assertEquals(PERIOD_15_CLOSE.get(1).getHash(), new ElectrumServer().getVerifiedHeader(32249).getHash());
        assertEquals(1, fake.getChunkRequests());
    }

    /**
     * A range reaches only as far as the nearest header already verified, so it does not necessarily cover the rest of its period: the heights above
     * that header need a range of their own, which the coalescing must not skip because a lower range shares their period.
     * <p>
     * The cache is seeded directly, since nothing ordinary produces that gap - a range is cached whole and ends at a verified header or the pin, so a
     * period's verified heights are always a run up to it. The coalescing should not have to rely on that.
     */
    @Test
    public void prefetchesTheHeightsAboveAnAlreadyVerifiedHeaderInThePeriod() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);
        ElectrumServer.verifiedHistoricalHeaders.put(32253, PERIOD_15_CLOSE.get(5));

        new ElectrumServer().prefetchVerifiedHeaders(List.of(32249, 32254));

        //One range up to the verified header, and one for what it leaves above
        assertEquals(2, fake.getChunkRequests());
        assertEquals(PERIOD_15_CLOSE.get(1).getHash(), new ElectrumServer().getVerifiedHeader(32249).getHash());
        assertEquals(PERIOD_15_CLOSE.get(6).getHash(), new ElectrumServer().getVerifiedHeader(32254).getHash());
        assertEquals(2, fake.getChunkRequests());
    }

    /**
     * A prefetched range that cannot be verified is simply not cached, which leaves the heights in it to be fetched singly and refused in the ordinary
     * way. It is never a failure of the pass, and never a partial cache.
     */
    @Test
    public void leavesAnUnverifiableRangeUncached() throws Exception {
        Network.set(Network.MAINNET);
        List<BlockHeader> tampered = new ArrayList<>(PERIOD_15_CLOSE);
        BlockHeader original = tampered.get(4);
        tampered.set(4, new BlockHeader(original.getVersion(), original.getPrevBlockHash(), original.getMerkleRoot(), null, original.getTime() + 1,
                original.getDifficultyTarget(), original.getNonce()));
        serveFrom(tampered, 32248);

        new ElectrumServer().prefetchVerifiedHeaders(List.of(32250));

        assertTrue(ElectrumServer.verifiedHistoricalHeaders.isEmpty());
    }

    /**
     * Heights above the last pin are the store's business, so the prefetch leaves them alone rather than asking for ranges below a pin they do not sit
     * under.
     */
    @Test
    public void prefetchesNothingForHeightsAboveTheLastPin() throws Exception {
        Network.set(Network.MAINNET);
        FakeElectrumServerRpc fake = serveFrom(PERIOD_15_CLOSE, 32248);

        int maxHeight = Network.MAINNET.getHeaderCheckpoints().getMaxHeight();
        new ElectrumServer().prefetchVerifiedHeaders(List.of(0, maxHeight + 1, maxHeight + 5000));

        assertEquals(0, fake.getChunkRequests());
    }

    @Test
    public void refusesAHistoricalRangeThatDoesNotLinkToItsPin() throws Exception {
        Network.set(Network.MAINNET);
        List<BlockHeader> tampered = new ArrayList<>(PERIOD_15_CLOSE);
        BlockHeader original = tampered.get(4);
        tampered.set(4, new BlockHeader(original.getVersion(), original.getPrevBlockHash(), original.getMerkleRoot(), null, original.getTime() + 1,
                original.getDifficultyTarget(), original.getNonce()));
        serveFrom(tampered, 32248);

        assertNull(new ElectrumServer().getVerifiedHeader(32250));
        assertTrue(ElectrumServer.verifiedHistoricalHeaders.isEmpty());
    }

    private static FakeElectrumServerRpc serveFrom(List<BlockHeader> chain, int baseHeight) {
        FakeElectrumServerRpc fake = new FakeElectrumServerRpc(chain, baseHeight);
        ElectrumServer.electrumServerRpc = fake;

        return fake;
    }

    /**
     * A run of the sync service happens on a background thread, so everything it touches has to be usable there. The connection check in particular
     * cannot be the AppServices one, which reads a JavaFX Service and throws off the application thread.
     */
    @Test
    public void runsTheServiceTaskFromABackgroundThread() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        HeaderStore store = seedStore(chain, 10);
        serve(chain);

        runOffThread(() -> {
            ElectrumServer.HeaderSyncService.syncAnnouncedHeaders(new ChainTip(12, chain.get(11)));
            return null;
        });

        assertEquals(12, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
    }

    /**
     * getTransport() creates a transport where there is none, so a run firing after the connection closed has to do nothing at all rather than open
     * one of its own outside the connection lifecycle.
     */
    @Test
    public void doesNotSyncOnceTheConnectionHasGone() throws Exception {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 12, CHAIN_TIME);
        HeaderStore store = seedStore(chain, 10);
        FakeElectrumServerRpc fake = serve(chain);
        ElectrumServer.transport = null;

        runOffThread(() -> {
            ElectrumServer.HeaderSyncService.syncAnnouncedHeaders(new ChainTip(12, chain.get(11)));
            return null;
        });

        assertEquals(10, store.getTipHeight());
        assertEquals(0, fake.getChunkRequests());
    }

    private static void runOffThread(Callable<Void> task) throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.submit(task).get(30, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static HeaderStore seedStore(List<BlockHeader> chain, int toHeight) throws Exception {
        HeaderStore store = ElectrumServer.getHeaderStore();
        for(int height = 1; height <= toHeight; height++) {
            store.append(chain.get(height - 1));
        }

        return store;
    }

    private static FakeElectrumServerRpc serve(List<BlockHeader> chain) {
        FakeElectrumServerRpc fake = new FakeElectrumServerRpc(chain);
        ElectrumServer.electrumServerRpc = fake;

        return fake;
    }

    /**
     * A chain sharing the given number of headers with the given one, and carrying the given number of its own above them.
     */
    private static List<BlockHeader> fork(List<BlockHeader> chain, int sharedHeaders, int branchHeaders) {
        List<BlockHeader> branch = new ArrayList<>(chain.subList(0, sharedHeaders));
        branch.addAll(mineChain(branch.getLast(), branchHeaders, BRANCH_TIME));

        return branch;
    }

    private static List<BlockHeader> mineChain(BlockHeader previous, int count, long startTime) {
        List<BlockHeader> chain = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            previous = mineHeader(previous, startTime + i);
            chain.add(previous);
        }

        return chain;
    }

    /**
     * A header that links but does not meet the target, which regtest's trivial difficulty makes as easy to produce as a valid one.
     */
    private static BlockHeader unmineHeader(BlockHeader previous, long time) {
        for(long nonce = 0; nonce < 1000; nonce++) {
            BlockHeader header = new BlockHeader(1, previous.getHash(), Sha256Hash.ZERO_HASH, null, time, 0x207fffffL, nonce);
            if(!header.verifyProofOfWork()) {
                return header;
            }
        }

        throw new IllegalStateException("Could not produce a regtest header below its target at time " + time);
    }

    private static BlockHeader mineHeader(BlockHeader previous, long time) {
        for(long nonce = 0; nonce < 1000; nonce++) {
            BlockHeader header = new BlockHeader(1, previous.getHash(), Sha256Hash.ZERO_HASH, null, time, 0x207fffffL, nonce);
            if(header.verifyProofOfWork()) {
                return header;
            }
        }

        throw new IllegalStateException("Could not mine a regtest header at time " + time);
    }

    /**
     * Answers header requests from a chain of the test's choosing, applying the same response checks a real server's answer is put through, and
     * counting what it was asked for.
     */
    private static class FakeElectrumServerRpc extends SimpleElectrumServerRpc {
        private final List<BlockHeader> chain;
        private final int baseHeight;               //the height of the header at index 0
        private final AtomicInteger chunkRequests = new AtomicInteger();
        private volatile int lastStartHeight;
        private volatile int lastCount;
        private volatile long responseDelayMillis;
        private volatile RuntimeException failure;
        private volatile BlockHeaders malformedResponse;

        public FakeElectrumServerRpc(List<BlockHeader> chain) {
            this(chain, 1);
        }

        public FakeElectrumServerRpc(List<BlockHeader> chain, int baseHeight) {
            this.chain = List.copyOf(chain);
            this.baseHeight = baseHeight;
        }

        @Override
        public BlockHeaders getBlockHeadersChunk(Transport transport, int startHeight, int count) {
            chunkRequests.incrementAndGet();
            lastStartHeight = startHeight;
            lastCount = count;
            if(failure != null) {
                throw failure;
            }
            if(malformedResponse != null) {
                return malformedResponse;       //returned unchecked, as an implementation that did not apply the response checks would
            }

            if(responseDelayMillis > 0) {
                try {
                    Thread.sleep(responseDelayMillis);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            int index = startHeight - baseHeight;
            int available = Math.max(0, Math.min(count, chain.size() - index));
            List<BlockHeader> headers = available == 0 ? Collections.emptyList() : chain.subList(index, index + available);
            BlockHeaders blockHeaders = new BlockHeaders();
            blockHeaders.count = headers.size();
            blockHeaders.max = HeaderChainState.RETARGET_INTERVAL;
            blockHeaders.hex = headers.stream().map(header -> Utils.bytesToHex(header.bitcoinSerialize())).collect(Collectors.joining());

            return ElectrumServerRpc.checkBlockHeaders(blockHeaders, startHeight, count, baseHeight + chain.size() - 1);
        }

        public int getChunkRequests() {
            return chunkRequests.get();
        }

        public int getLastStartHeight() {
            return lastStartHeight;
        }

        public int getLastCount() {
            return lastCount;
        }

        public void setResponseDelayMillis(long responseDelayMillis) {
            this.responseDelayMillis = responseDelayMillis;
        }

        public void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        public void setMalformedResponse(BlockHeaders malformedResponse) {
            this.malformedResponse = malformedResponse;
        }
    }

    /**
     * The event is dispatched on the thread that reconciled, so it is captured by the time the call returns.
     */
    public static class ChainReorgListener {
        private volatile Integer forkHeight;

        @Subscribe
        public void chainReorg(ChainReorgEvent event) {
            forkHeight = event.getForkHeight();
        }

        public Integer getForkHeight() {
            return forkHeight;
        }
    }

    /**
     * A transport that reports itself connected without opening a socket, since the connection check reads the transport rather than the connection service.
     */
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
