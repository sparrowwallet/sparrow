package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.HeaderCheckpoints;
import com.sparrowwallet.drongo.protocol.ProtocolException;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.List;

/**
 * The block headers above the last pinned checkpoint, held as a flat file of consecutive raw 80 byte records named for the height of its first record,
 * which is the height following the last pin. The height h is stored at offset (h - startHeight) * 80, and the first record must descend from the pin,
 * so that the compiled-in checkpoints and the file agree on where each height lies.
 * <p>
 * Nothing in the file is trusted: the whole chain is re-verified from the pinned anchor on every load, which costs a few tens of milliseconds per year
 * of headers. A torn, truncated or tampered file therefore costs a re-download of the headers above the damage rather than admitting an unverified
 * header. A file that does not descend from the last pin at all - another network's, or one copied from another machine - is discarded.
 * <p>
 * A store written against an earlier checkpoint set is superseded rather than read: everything below the new anchor is unreachable and what is above it
 * is a small re-download, so deleting it keeps the file to exactly what this release's checkpoints verify, from its first record.
 */
public class HeaderStore {
    private static final Logger log = LoggerFactory.getLogger(HeaderStore.class);

    public static final int HEADER_LENGTH = 80;

    private final File file;
    private final int startHeight;      //the height of the record at offset zero, being the header immediately above the last pin
    private final HeaderCheckpoints checkpoints;
    private HeaderChainState chainState;    //the live state at the store tip, rebuilt from the anchor whenever the file is truncated

    private HeaderStore(File file, HeaderCheckpoints checkpoints) {
        this.file = file;
        this.startHeight = checkpoints.getMaxHeight() + 1;
        this.checkpoints = checkpoints;
    }

    /**
     * Loads (or creates) the store for the given checkpoints, fully re-verifying the chain from the pinned anchor.
     */
    public static synchronized HeaderStore load(HeaderCheckpoints checkpoints) throws IOException {
        int startHeight = checkpoints.getMaxHeight() + 1;
        File headersDir = Storage.getHeadersDir();
        deleteSupersededStores(headersDir, startHeight);

        HeaderStore store = new HeaderStore(new File(headersDir, Integer.toString(startHeight)), checkpoints);
        if(!store.isAnchored()) {
            log.warn("Discarding the block header store at " + store.file.getName() + ": it does not descend from the checkpoint at height " + checkpoints.getMaxHeight());
            if(!store.file.delete()) {
                //Only the clearer message is lost: the walk below refuses the first record and empties the file to the same end
                log.debug("Could not delete the block header store at " + store.file.getAbsolutePath());
            }
        }

        store.open();

        return store;
    }

    /**
     * Deletes the stores written against earlier checkpoint sets, whose records this one cannot use. A store starting above this anchor is from a later
     * release and is left alone, so that a downgrade does not cost the upgrade its headers.
     */
    private static void deleteSupersededStores(File headersDir, int startHeight) {
        File[] files = headersDir.listFiles();
        if(files == null) {
            return;
        }

        for(File file : files) {
            try {
                int base = Integer.parseInt(file.getName());
                if(base >= 0 && base < startHeight && !file.delete()) {
                    //Nothing reads a superseded store, so leaving one behind costs the space it holds and nothing else
                    log.warn("Could not delete the superseded block header store at " + file.getAbsolutePath());
                }
            } catch(NumberFormatException e) {
                //Not a header store file
            }
        }
    }

    /**
     * Verifies and appends a header extending the store tip, writing it only once the chain state has accepted it.
     */
    public synchronized void append(BlockHeader header) throws IOException {
        append(List.of(header));
    }

    /**
     * Verifies and appends a run of headers extending the store tip, writing in one pass what the chain state accepted. A run carrying a header that
     * the chain state rejects appends the headers below it and throws, leaving the store exactly where appending them one at a time would.
     */
    public synchronized void append(List<BlockHeader> headers) throws IOException {
        long offset = getOffset(chainState.getHeight() + 1);
        ByteArrayOutputStream accepted = new ByteArrayOutputStream(headers.size() * HEADER_LENGTH);
        VerificationException rejected = null;
        try {
            for(BlockHeader header : headers) {
                chainState.add(header);
                accepted.writeBytes(header.bitcoinSerialize());
            }
        } catch(VerificationException e) {
            rejected = e;
        }

        if(accepted.size() > 0) {
            try(RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                randomAccessFile.seek(offset);
                randomAccessFile.write(accepted.toByteArray());
            } catch(IOException e) {
                rebuild();      //the chain state and the file must agree on the tip, whatever of the run was written
                throw e;
            }
        }

        if(rejected != null) {
            throw rejected;
        }
    }

    /**
     * Drops every header above the given height, which becomes the new tip.
     */
    public synchronized void truncate(int newTipHeight) throws IOException {
        if(newTipHeight >= chainState.getHeight()) {
            return;
        }

        setLength(Math.max(0, getOffset(newTipHeight + 1)));
        rebuild();
    }

    /**
     * The header at the given height, or null where it is below the anchor or above the tip.
     */
    public synchronized BlockHeader getHeader(int height) throws IOException {
        if(height < startHeight || height > chainState.getHeight()) {
            return null;
        }

        byte[] record = readRecord(getOffset(height));
        return record == null ? null : new BlockHeader(record, 0);
    }

    /**
     * The hash at the given height, which at the anchor itself is the pinned hash.
     */
    public synchronized Sha256Hash getHash(int height) throws IOException {
        if(height == startHeight - 1) {
            return checkpoints.getHash(checkpoints.getMaxHeight());
        }

        BlockHeader header = getHeader(height);
        return header == null ? null : header.getHash();
    }

    /**
     * Whether the file still holds exactly what the chain state says it does. A file removed or truncated underneath a loaded store would otherwise
     * have every height read as absent, which surfaces as a server unable to substantiate a chain it served rather than as the local fault it is.
     */
    public synchronized boolean isIntact() throws IOException {
        return file.length() == getOffset(chainState.getHeight() + 1);
    }

    /**
     * The lowest height this store serves, being the header immediately above the last pin.
     */
    public synchronized int getStartHeight() {
        return startHeight;
    }

    /**
     * The height of the last verified header, which for an empty store is the anchor itself.
     */
    public synchronized int getTipHeight() {
        return chainState.getHeight();
    }

    public synchronized Sha256Hash getTipHash() {
        return chainState.getHash();
    }

    /**
     * The chain work accumulated above the pinned anchor, measured under the network's own rule. A candidate chain validated on a state re-walked to a
     * fork point shares that anchor, so the two are directly comparable and the shared work below the fork cancels.
     */
    public synchronized BigInteger getChainWork() {
        return chainState.getChainWork();
    }

    /**
     * A throwaway chain state re-walked from the anchor to the given height, on which a reorg candidate can be validated. A rolling state cannot be
     * rewound, so this walk is what a fork point costs.
     */
    public synchronized HeaderChainState chainStateAt(int height) throws IOException {
        if(height < startHeight - 1 || height > chainState.getHeight()) {
            throw new IllegalArgumentException("Height " + height + " is outside the store range " + (startHeight - 1) + " to " + chainState.getHeight());
        }

        HeaderChainState state = walkTo(getOffset(height + 1));
        if(state.getHeight() != height) {
            throw new VerificationException("The block header store could not be re-verified to height " + height + ", reaching only height " + state.getHeight());
        }

        return state;
    }

    private void open() throws IOException {
        if(!file.exists()) {
            if(!file.createNewFile()) {
                throw new IOException("Could not create the block header store at " + file.getAbsolutePath());
            }

            chainState = checkpoints.newChainState();
            return;
        }

        //A header interrupted mid write costs only itself: the tip append is deliberately not synced, and what is lost is fetched again
        long records = file.length() / HEADER_LENGTH;
        if(records * HEADER_LENGTH != file.length()) {
            setLength(records * HEADER_LENGTH);
        }

        rebuild();
    }

    private void rebuild() throws IOException {
        HeaderChainState state = walkTo(file.length());
        long verifiedLength = getOffset(state.getHeight() + 1);
        if(file.length() > verifiedLength) {
            setLength(verifiedLength);
        }

        chainState = state;
    }

    /**
     * Re-verifies the records below the given offset from the pinned anchor, stopping at the first that does not extend the chain.
     */
    private HeaderChainState walkTo(long endOffset) throws IOException {
        HeaderChainState state = checkpoints.newChainState();
        if(endOffset < HEADER_LENGTH) {
            return state;
        }

        try(DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] record = new byte[HEADER_LENGTH];
            for(long offset = 0; offset + HEADER_LENGTH <= endOffset; offset += HEADER_LENGTH) {
                inputStream.readFully(record);
                try {
                    state.add(new BlockHeader(record, 0));
                } catch(VerificationException | ProtocolException e) {
                    log.warn("Dropping the block header store above height " + state.getHeight() + ": " + e.getMessage());
                    break;
                }
            }
        }

        return state;
    }

    /**
     * Whether the first record descends from the last pin. A store that holds no records yet cannot contradict it.
     */
    private boolean isAnchored() throws IOException {
        byte[] record = readRecord(0);
        return record == null || new BlockHeader(record, 0).getPrevBlockHash().equals(checkpoints.getHash(checkpoints.getMaxHeight()));
    }

    private byte[] readRecord(long offset) throws IOException {
        if(offset < 0 || file.length() < offset + HEADER_LENGTH) {
            return null;
        }

        try(RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            randomAccessFile.seek(offset);
            byte[] record = new byte[HEADER_LENGTH];
            randomAccessFile.readFully(record);

            return record;
        }
    }

    private void setLength(long length) throws IOException {
        try(RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.setLength(length);
        }
    }

    private long getOffset(int height) {
        return (long)(height - startHeight) * HEADER_LENGTH;
    }
}
