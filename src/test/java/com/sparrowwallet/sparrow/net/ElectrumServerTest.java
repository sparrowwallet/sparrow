package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ElectrumServerTest {
    @TempDir
    private static Path tempHome;

    //A plain BIP32 extended public key, since the wallet only needs to derive addresses to have script hashes
    private static final String TEST_XPUB = "xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj";

    private static final String GENESIS_HEADER_HEX = "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c";
    private static final long GENESIS_TIME_SECS = 1231006505L;

    private static final String BLOCK_800000_HEADER_HEX = "00601d3455bb9fbd966b3ea2dc42d0c22722e4c0c1729fad17210100000000000000000055087fab0c8f3f89f8bcfd4df26c504d81b0a88e04907161838c0c53001af09135edbd64943805175e955e06";
    private static final long BLOCK_800000_TIME_SECS = 1690168629L;

    @BeforeAll
    public static void setUpAll() {
        //Config.get() caches its instance for the life of the JVM but resolves the file to write on each flush, so a test changing a setting must
        //never be able to reach the developer's own Sparrow home. The test task sets this too; this is here for a run that bypasses it
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() {
        Network.set(Network.MAINNET);
    }

    @Test
    public void acceptsRealHeader() {
        assertNull(ElectrumServer.getTipValidationError(tip(800000, BLOCK_800000_HEADER_HEX), (BLOCK_800000_TIME_SECS + 3600) * 1000));
    }

    /**
     * Proof of work is checked against the header's own claimed target, so a genuine difficulty 1 header is valid on its own terms, however old it is and
     * whatever height it is announced at. Establishing that a header belongs to the chain at the announced height requires linkage from a known checkpoint.
     */
    @Test
    public void acceptsGenuineHeaderRegardlessOfAgeAndAnnouncedHeight() {
        assertNull(ElectrumServer.getTipValidationError(tip(0, GENESIS_HEADER_HEX), (GENESIS_TIME_SECS + 3600) * 1000));
        assertNull(ElectrumServer.getTipValidationError(tip(950000, GENESIS_HEADER_HEX), System.currentTimeMillis()));
    }

    @Test
    public void rejectsFutureTimestampedHeader() {
        assertNotNull(ElectrumServer.getTipValidationError(tip(800000, BLOCK_800000_HEADER_HEX), (BLOCK_800000_TIME_SECS - 5 * 60 * 60) * 1000));
    }

    @Test
    public void rejectsTamperedHeader() {
        byte[] tampered = Utils.hexToBytes(BLOCK_800000_HEADER_HEX);
        tampered[79] ^= 0x01;
        assertNotNull(ElectrumServer.getTipValidationError(tip(800000, Utils.bytesToHex(tampered)), (BLOCK_800000_TIME_SECS + 3600) * 1000));
    }

    @Test
    public void rejectsMalformedTips() {
        long now = (BLOCK_800000_TIME_SECS + 3600) * 1000;
        assertNotNull(ElectrumServer.getTipValidationError(tip(800000, null), now));
        assertNotNull(ElectrumServer.getTipValidationError(tip(-1, BLOCK_800000_HEADER_HEX), now));
        assertNotNull(ElectrumServer.getTipValidationError(tip(800000, "cafebabe"), now));
    }

    /**
     * A header below a pinned checkpoint is placed at its height by descent from the pin, so the hash chain up to the pinned hash is the whole proof:
     * no proof of work, difficulty or timestamp check is applied to a range that ends in a hash already known to be on the chain.
     */
    @Test
    public void verifiesHeadersLinkedToTheAnchor() {
        List<BlockHeader> headers = chain(5);
        List<BlockHeader> linked = ElectrumServer.getLinkedHeaders(headersChunk(headers), 5, headers.getLast().getHash());
        assertNotNull(linked);
        assertEquals(headers.getFirst().getHash(), linked.getFirst().getHash());
        assertEquals(headers.getLast().getHash(), linked.getLast().getHash());
    }

    @Test
    public void rejectsHeadersThatDoNotReachTheAnchor() {
        List<BlockHeader> headers = chain(5);

        //The range is internally consistent, but ends somewhere other than the pinned hash
        assertNull(ElectrumServer.getLinkedHeaders(headersChunk(headers), 5, headers.get(3).getHash()));
    }

    @Test
    public void rejectsATamperedHeaderWithinTheRange() {
        List<BlockHeader> headers = new ArrayList<>(chain(5));
        BlockHeader tampered = headers.get(2);
        headers.set(2, new BlockHeader(tampered.getVersion(), tampered.getPrevBlockHash(), Sha256Hash.ZERO_HASH, null, tampered.getTime() + 1,
                tampered.getDifficultyTarget(), tampered.getNonce()));

        //The header above it still carries the original hash, so the chain to the anchor is broken at the substitution
        assertNull(ElectrumServer.getLinkedHeaders(headersChunk(headers), 5, headers.getLast().getHash()));
    }

    @Test
    public void rejectsAShortOrMalformedRange() {
        List<BlockHeader> headers = chain(5);
        assertNull(ElectrumServer.getLinkedHeaders(headersChunk(headers.subList(0, 4)), 5, headers.getLast().getHash()));

        BlockHeaders malformed = headersChunk(headers);
        malformed.hex = "cafebabe";
        assertNull(ElectrumServer.getLinkedHeaders(malformed, 5, headers.getLast().getHash()));
    }

    private static List<BlockHeader> chain(int count) {
        List<BlockHeader> headers = new ArrayList<>();
        Sha256Hash previousHash = Sha256Hash.ZERO_HASH;
        for(int i = 0; i < count; i++) {
            BlockHeader header = new BlockHeader(1, previousHash, Sha256Hash.ZERO_HASH, null, 1600000000L + i, 0x1d00ffffL, i);
            headers.add(header);
            previousHash = header.getHash();
        }

        return headers;
    }

    private static BlockHeaders headersChunk(List<BlockHeader> headers) {
        BlockHeaders blockHeaders = new BlockHeaders();
        blockHeaders.count = headers.size();
        blockHeaders.hex = headers.stream().map(header -> Utils.bytesToHex(header.bitcoinSerialize())).collect(Collectors.joining());
        blockHeaders.max = HeaderChainState.RETARGET_INTERVAL;

        return blockHeaders;
    }

    /**
     * A reorg invalidates only the nodes holding something above the fork point. A wallet with nothing there has proven nothing against a header that
     * was discarded, so it reports no invalidation and its handler leaves it alone rather than joining a refresh of every open wallet.
     */
    @Test
    public void invalidatesOnlyTheNodesHoldingSomethingAboveTheFork() {
        Wallet wallet = testWallet();
        WalletNode receiveNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        receiveNode.getTransactionOutputs().add(new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 800000, new Date(), 0L, 0, 10000));

        assertFalse(ElectrumServer.invalidateScriptHashesForReorg(wallet, 800000));
        assertFalse(ElectrumServer.invalidateScriptHashesForReorg(wallet, 900000));
        assertTrue(ElectrumServer.invalidateScriptHashesForReorg(wallet, 799999));
    }

    /**
     * A spend confirmed in the orphaned block is the same case as an output received in it: the node holding the output it spent has to be refetched.
     */
    @Test
    public void invalidatesANodeWhoseOutputWasSpentAboveTheFork() {
        Wallet wallet = testWallet();
        WalletNode receiveNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        BlockTransactionHashIndex output = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 700000, new Date(), 0L, 0, 10000);
        output.setSpentBy(new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 800000, new Date(), 0L, 0, 10000));
        receiveNode.getTransactionOutputs().add(output);

        //The output itself is far below the fork, but the spend of it is not
        assertTrue(ElectrumServer.invalidateScriptHashesForReorg(wallet, 799999));
        assertFalse(ElectrumServer.invalidateScriptHashesForReorg(wallet, 800000));
    }

    /**
     * A server that has not reached the last pinned header cannot substantiate any height: the forward sync has nothing to advance from and every
     * range below a pin comes back short. Verification therefore does not run against it at all until it catches up, rather than refusing every new
     * confirmation and raising a dialog for each. The public tier rejects such a server at connect instead; this is what a private one gets.
     */
    @Test
    public void doesNotVerifyAgainstAServerBelowTheLastPin() {
        ServerCapability previousCapability = ElectrumServer.serverCapability;
        try {
            ElectrumServer.serverCapability = new ServerCapability(false, false, false);
            int maxCheckpointHeight = Network.MAINNET.getHeaderCheckpoints().getMaxHeight();
            BlockHeader header = Network.MAINNET.getGenesisHeader();

            //A tip that has not been announced yet is not evidence of lagging, so the sync and the proofs are left to find out
            AppServices.setAnnouncedTip(null);
            assertTrue(ElectrumServer.isVerifyingTransactions());

            AppServices.setAnnouncedTip(new ChainTip(maxCheckpointHeight - 1, header));
            assertFalse(ElectrumServer.isVerifyingTransactions());

            AppServices.setAnnouncedTip(new ChainTip(maxCheckpointHeight, header));
            assertTrue(ElectrumServer.isVerifyingTransactions());

            ElectrumServer.serverCapability.withMerkleProofs(false);
            assertFalse(ElectrumServer.isVerifyingTransactions());
        } finally {
            ElectrumServer.serverCapability = previousCapability;
            AppServices.setAnnouncedTip(null);
        }
    }

    /**
     * The escape hatch. It defaults on and has no user interface, and one answer has to cover the header sync, both write boundaries and the connect
     * time enforcement - a public server rejected for lacking a call nothing is going to make would be no use.
     */
    @Test
    public void stopsVerifyingWhereTheConfigTurnsItOff() {
        ServerCapability previousCapability = ElectrumServer.serverCapability;
        ServerType previousServerType = Config.get().getServerType();
        try {
            ElectrumServer.serverCapability = new ServerCapability(false, false, false);
            Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
            assertTrue(Config.get().isVerifyTransactions());
            assertTrue(ElectrumServer.isVerifyingTransactions());
            assertTrue(ElectrumServer.isVerificationMandatory());

            Config.get().setVerifyTransactions(false);
            assertFalse(ElectrumServer.isVerifyingTransactions());
            assertFalse(ElectrumServer.isVerificationMandatory());
        } finally {
            Config.get().setVerifyTransactions(true);
            Config.get().setServerType(previousServerType);
            ElectrumServer.serverCapability = previousCapability;
            AppServices.setAnnouncedTip(null);
        }
    }

    /**
     * A Bitcoin Core connection is the user's own node, and a proof it built against headers it also supplied establishes nothing it has not already
     * been trusted for. Asked of the server type rather than of the capability, because bwt takes over where cormorant cannot start - a legacy Core
     * wallet, or an unsupported bitcoind - and it reaches getServerCapability under a version string of its own.
     */
    @Test
    public void doesNotVerifyAgainstTheUsersOwnNode() {
        ServerCapability previousCapability = ElectrumServer.serverCapability;
        ServerType previousServerType = Config.get().getServerType();
        try {
            //The capability bwt falls through to, which unlike cormorant's says nothing about proofs
            ElectrumServer.serverCapability = new ServerCapability(false, true, true);
            assertTrue(ElectrumServer.serverCapability.supportsMerkleProofs());

            Config.get().setServerType(ServerType.ELECTRUM_SERVER);
            assertTrue(ElectrumServer.isVerifyingTransactions());

            Config.get().setServerType(ServerType.BITCOIN_CORE);
            assertFalse(ElectrumServer.isVerifyingTransactions());
            assertFalse(ElectrumServer.isVerificationMandatory());
        } finally {
            Config.get().setServerType(previousServerType);
            ElectrumServer.serverCapability = previousCapability;
            AppServices.setAnnouncedTip(null);
        }
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

    private BlockHeaderTip tip(int height, String hex) {
        BlockHeaderTip tip = new BlockHeaderTip();
        tip.height = height;
        tip.hex = hex;
        return tip;
    }

    @AfterEach
    public void tearDown() throws Exception {
        //The reorg tests above invalidate script hashes, which is the one piece of static state they leave behind
        ElectrumServer.reorgInvalidatedScriptHashes.clear();
        Network.set(null);
    }
}
