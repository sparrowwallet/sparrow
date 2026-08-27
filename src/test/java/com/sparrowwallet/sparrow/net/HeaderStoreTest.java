package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderCheckpoints;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The header store on regtest, whose trivial proof of work target is the only one a synthetic chain can be mined against, and whose empty checkpoints
 * anchor the store at the genesis header. Every load re-verifies the file from that anchor, so these tests are as much about what a damaged file
 * costs - the headers above the damage, never an unverified header - as about the round trip.
 */
public class HeaderStoreTest {
    @TempDir
    private static Path tempHome;

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
    }

    @AfterEach
    public void tearDown() {
        Network.set(null);
    }

    @Test
    public void storesAndReloadsAChain() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 20);
        HeaderStore store = HeaderStore.load(checkpoints());

        //An empty store is the anchor itself, and serves no heights at all
        assertEquals(0, store.getTipHeight());
        assertEquals(Network.REGTEST.getGenesisHash(), store.getTipHash());
        assertEquals(1, store.getStartHeight());
        assertNull(store.getHeader(0));
        assertNull(store.getHeader(1));

        for(BlockHeader header : chain) {
            store.append(header);
        }

        assertEquals(20, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertEquals(20 * HeaderStore.HEADER_LENGTH, storeFile("1").length());

        HeaderStore reloaded = HeaderStore.load(checkpoints());
        assertEquals(20, reloaded.getTipHeight());
        for(int height = 1; height <= 20; height++) {
            assertEquals(chain.get(height - 1).getHash(), reloaded.getHeader(height).getHash());
            assertEquals(chain.get(height - 1).getHash(), reloaded.getHash(height));
        }

        //The anchor is the pinned hash rather than a stored record, and heights below it are not served
        assertEquals(Network.REGTEST.getGenesisHash(), reloaded.getHash(0));
        assertNull(reloaded.getHeader(0));
        assertNull(reloaded.getHeader(21));
    }

    @Test
    public void refusesAHeaderThatDoesNotExtendTheChain() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 5);
        HeaderStore store = HeaderStore.load(checkpoints());
        store.append(chain.getFirst());

        //A header that does not link is refused before anything is written, so the store is left exactly as it was
        assertThrows(VerificationException.class, () -> store.append(chain.getLast()));
        assertEquals(1, store.getTipHeight());
        assertEquals(HeaderStore.HEADER_LENGTH, storeFile("1").length());
    }

    /**
     * A run carrying a header the chain state rejects appends the headers below it and stops there, which is what appending them one at a time did:
     * the store keeps the progress it verified, and the run is refused from the header that failed.
     */
    @Test
    public void appendsTheVerifiedPrefixOfARejectedRun() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 2, 1600000000L);
        BlockHeader belowTarget = unmineHeader(chain.getLast(), 1600000002L);
        List<BlockHeader> run = new ArrayList<>(chain);
        run.add(belowTarget);
        run.addAll(mineChain(belowTarget, 2, 1600000003L));

        HeaderStore store = HeaderStore.load(checkpoints());
        VerificationException e = assertThrows(VerificationException.class, () -> store.append(run));
        assertTrue(e.getMessage().contains("proof of work"), e.getMessage());

        assertEquals(2, store.getTipHeight());
        assertEquals(chain.getLast().getHash(), store.getTipHash());
        assertEquals(2 * HeaderStore.HEADER_LENGTH, storeFile("1").length());
        assertEquals(2, HeaderStore.load(checkpoints()).getTipHeight());
    }

    @Test
    public void repairsATornWrite() throws IOException {
        appendChain(mineChain(Network.REGTEST.getGenesisHeader(), 10));

        //The tip append is deliberately not synced, so a process killed mid write leaves a partial record
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(storeFile("1"), "rw")) {
            randomAccessFile.seek(randomAccessFile.length());
            randomAccessFile.write(new byte[HeaderStore.HEADER_LENGTH / 2]);
        }

        HeaderStore store = HeaderStore.load(checkpoints());
        assertEquals(10, store.getTipHeight());
        assertEquals(10 * HeaderStore.HEADER_LENGTH, storeFile("1").length());
    }

    @Test
    public void dropsTheHeadersAboveACorruptedRecord() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10);
        appendChain(chain);

        //Record 9 is the header at height 10, which no longer links to the header below it
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(storeFile("1"), "rw")) {
            long position = 9 * HeaderStore.HEADER_LENGTH + 4;
            randomAccessFile.seek(position);
            int previousHashByte = randomAccessFile.read();
            randomAccessFile.seek(position);
            randomAccessFile.write(previousHashByte ^ 0xff);
        }

        HeaderStore store = HeaderStore.load(checkpoints());
        assertEquals(9, store.getTipHeight());
        assertEquals(chain.get(8).getHash(), store.getTipHash());
        assertEquals(9 * HeaderStore.HEADER_LENGTH, storeFile("1").length());

        //And the store carries on from there
        store.append(chain.get(9));
        assertEquals(10, store.getTipHeight());
    }

    /**
     * A store written against an earlier checkpoint set cannot be read at this one's offsets, and holds nothing above the new anchor that is not a
     * small re-download, so it is deleted rather than carried forward. Regtest anchors at genesis, so a file starting at height 0 is that shape.
     */
    @Test
    public void supersedesAStoreFromAnEarlierCheckpointSet() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10);
        List<BlockHeader> records = new ArrayList<>();
        records.add(Network.REGTEST.getGenesisHeader());
        records.addAll(chain);
        writeStoreFile("0", records);
        writeStoreFile("1", chain);

        HeaderStore store = HeaderStore.load(checkpoints());
        assertFalse(storeFile("0").exists());

        //The store for this anchor is the one that is read, and it starts at its own first record
        assertEquals(10, store.getTipHeight());
        assertEquals(1, store.getStartHeight());
        assertNull(store.getHeader(0));
        assertEquals(chain.getFirst().getHash(), store.getHeader(1).getHash());
        assertEquals(chain.getLast().getHash(), store.getHeader(10).getHash());

        BlockHeader next = mineChain(chain.getLast(), 1).getFirst();
        store.append(next);
        assertEquals(11, store.getTipHeight());
        assertEquals(11 * HeaderStore.HEADER_LENGTH, storeFile("1").length());
        assertEquals(next.getHash(), HeaderStore.load(checkpoints()).getHeader(11).getHash());
    }

    /**
     * A superseded store is deleted even where this checkpoint set has no store of its own yet, so the space it holds is not carried indefinitely.
     */
    @Test
    public void deletesASupersededStoreWithNothingToReplaceIt() throws IOException {
        writeStoreFile("0", mineChain(Network.REGTEST.getGenesisHeader(), 4));

        HeaderStore store = HeaderStore.load(checkpoints());
        assertFalse(storeFile("0").exists());
        assertTrue(storeFile("1").exists());
        assertEquals(0, store.getTipHeight());
        assertEquals(Network.REGTEST.getGenesisHash(), store.getTipHash());
        assertEquals(0, storeFile("1").length());
    }

    @Test
    public void ignoresAStoreStartingAboveTheAnchor() throws IOException {
        //A file written by a later release, whose checkpoints reach higher than these: it cannot be read at these offsets, so a fresh one is started
        writeStoreFile("500", mineChain(Network.REGTEST.getGenesisHeader(), 3));

        HeaderStore store = HeaderStore.load(checkpoints());
        assertEquals(0, store.getTipHeight());
        assertTrue(storeFile("500").exists());
        assertTrue(storeFile("1").exists());
    }

    @Test
    public void discardsAStoreThatDoesNotDescendFromTheAnchor() throws IOException {
        //Another network's headers, or a file copied from another machine: the record above the pin does not descend from it
        BlockHeader foreign = new BlockHeader(1, Sha256Hash.wrap("00000000000000000000000000000000000000000000000000000000deadbeef"),
                Sha256Hash.ZERO_HASH, null, 1600000000L, 0x207fffffL, 0);
        writeStoreFile("1", List.of(foreign));

        HeaderStore store = HeaderStore.load(checkpoints());
        assertEquals(0, store.getTipHeight());
        assertEquals(0, storeFile("1").length());
    }

    /**
     * Removing a store that cannot be read is a cleanup, not a precondition: where the file cannot be deleted the load must still produce a working
     * store rather than failing the session, since a failure here reaches the wallet as a server error on every height for as long as it lasts.
     */
    @Test
    public void loadsAStoreThatCannotBeDeleted() throws IOException {
        //Windows has no way to deny the removal: setWritable(false) is refused on a directory, and the read only attribute would not stop a delete in any case
        assumeFalse(OsType.getCurrent() == OsType.WINDOWS);

        BlockHeader foreign = new BlockHeader(1, Sha256Hash.wrap("00000000000000000000000000000000000000000000000000000000deadbeef"),
                Sha256Hash.ZERO_HASH, null, 1600000000L, 0x207fffffL, 0);
        writeStoreFile("1", List.of(foreign));

        //Where the directory denies removal the file is emptied in place instead, which is the same end state
        assertTrue(Storage.getHeadersDir().setWritable(false));
        try {
            HeaderStore store = HeaderStore.load(checkpoints());
            assertEquals(0, store.getTipHeight());
            assertEquals(Network.REGTEST.getGenesisHash(), store.getTipHash());
            assertEquals(0, storeFile("1").length());

            BlockHeader header = mineChain(Network.REGTEST.getGenesisHeader(), 1).getFirst();
            store.append(header);
            assertEquals(1, store.getTipHeight());
            assertEquals(header.getHash(), store.getTipHash());
        } finally {
            assertTrue(Storage.getHeadersDir().setWritable(true));
        }
    }

    @Test
    public void reorganisesToAChainOfAtLeastTheSameWork() throws IOException {
        List<BlockHeader> chain = mineChain(Network.REGTEST.getGenesisHeader(), 10);
        HeaderStore store = HeaderStore.load(checkpoints());
        for(BlockHeader header : chain) {
            store.append(header);
        }

        //Chain work on the test networks is the header count, so five headers above the fork point is what the branch has to beat
        assertEquals(BigInteger.valueOf(10), store.getChainWork());
        assertEquals(BigInteger.valueOf(5), store.chainStateAt(5).getChainWork());
        assertEquals(5, store.chainStateAt(5).getHeight());
        assertEquals(chain.get(4).getHash(), store.chainStateAt(5).getHash());

        //A branch from height 5 that is one header longer, mined at a later time so that it is a different chain
        List<BlockHeader> branch = mineChain(chain.get(4), 6, 1700000000L);
        store.truncate(5);
        assertEquals(5, store.getTipHeight());
        assertEquals(chain.get(4).getHash(), store.getTipHash());
        assertNull(store.getHeader(6));
        for(BlockHeader header : branch) {
            store.append(header);
        }

        assertEquals(11, store.getTipHeight());
        assertEquals(BigInteger.valueOf(11), store.getChainWork());
        assertEquals(branch.getLast().getHash(), store.getTipHash());

        HeaderStore reloaded = HeaderStore.load(checkpoints());
        assertEquals(11, reloaded.getTipHeight());
        assertEquals(branch.getFirst().getHash(), reloaded.getHeader(6).getHash());
        assertEquals(11 * HeaderStore.HEADER_LENGTH, storeFile("1").length());
    }

    @Test
    public void truncatesToTheAnchorItself() throws IOException {
        appendChain(mineChain(Network.REGTEST.getGenesisHeader(), 4));

        HeaderStore store = HeaderStore.load(checkpoints());
        store.truncate(0);
        assertEquals(0, store.getTipHeight());
        assertEquals(Network.REGTEST.getGenesisHash(), store.getTipHash());
        assertEquals(0, storeFile("1").length());
        assertThrows(IllegalArgumentException.class, () -> store.chainStateAt(1));
    }

    private static HeaderCheckpoints checkpoints() {
        HeaderCheckpoints checkpoints = Network.REGTEST.getHeaderCheckpoints();
        assertEquals(0, checkpoints.getMaxHeight());

        return checkpoints;
    }

    private static File storeFile(String name) {
        return new File(Storage.getHeadersDir(), name);
    }

    private static void appendChain(List<BlockHeader> chain) throws IOException {
        HeaderStore store = HeaderStore.load(checkpoints());
        for(BlockHeader header : chain) {
            store.append(header);
        }
    }

    private static void writeStoreFile(String name, List<BlockHeader> headers) throws IOException {
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(new File(Storage.getHeadersDir(), name), "rw")) {
            for(BlockHeader header : headers) {
                randomAccessFile.write(header.bitcoinSerialize());
            }
        }
    }

    private static List<BlockHeader> mineChain(BlockHeader previous, int count) {
        return mineChain(previous, count, 1600000000L);
    }

    private static List<BlockHeader> mineChain(BlockHeader previous, int count, long startTime) {
        List<BlockHeader> chain = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            previous = mineHeader(previous, startTime + i);
            chain.add(previous);
        }

        return chain;
    }

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
}
