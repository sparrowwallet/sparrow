package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.HeaderCheckpoints;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the pinned header hashes compiled into drongo against live public Electrum servers. This is a release checklist step rather than a unit
 * test - it needs the network and it is checking data, not code - so it is tagged out of the default test task and run with the verifyCheckpoint task.
 * <p>
 * A server that cannot be reached is skipped; a server that answers with a different chain fails the run. Checkpoint generation is a derivation from a
 * locally validated header chain, so this confirms independently that what was derived matches what the network says, and that the last pin is buried.
 */
@Tag("checkpoint")
public class HeaderCheckpointVerificationTest {
    /**
     * The number of blocks the last pinned header must be buried by. A pin that is later orphaned would have every install refuse the real chain at
     * that height, so a period is only pinned once it is beyond any plausible reorg.
     */
    private static final int CHECKPOINT_BURIAL_DEPTH = 6;

    private static final int PINS_VERIFIED = 3;
    private static final int MINIMUM_RESPONDERS = 3;

    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setUp() {
        //Config.get() caches its instance statically for the life of the JVM, so keep this test from loading the developer's real config
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void verifyMainnetCheckpoints() {
        verifyCheckpoints(Network.MAINNET);
    }

    @Test
    public void verifyTestnetCheckpoints() {
        verifyCheckpoints(Network.TESTNET);
    }

    @Test
    public void verifyTestnet4Checkpoints() {
        verifyCheckpoints(Network.TESTNET4);
    }

    @Test
    public void verifySignetCheckpoints() {
        verifyCheckpoints(Network.SIGNET);
    }

    private void verifyCheckpoints(Network network) {
        Network.set(network);

        HeaderCheckpoints checkpoints = network.getHeaderCheckpoints();
        List<PublicElectrumServer> servers = PublicElectrumServer.getServers();
        assertTrue(!servers.isEmpty(), "No public servers configured for " + network);

        List<String> unreachable = new ArrayList<>();
        int responders = 0;
        for(PublicElectrumServer publicServer : servers) {
            try {
                verifyAgainstServer(publicServer, checkpoints);
                responders++;
            } catch(AssertionError e) {
                throw new AssertionError(publicServer.getServer().getHostAndPort() + " disagrees with the " + network + " checkpoints: " + e.getMessage(), e);
            } catch(Exception e) {
                unreachable.add(publicServer.getServer().getHostAndPort() + " (" + e.getMessage() + ")");
            }
        }

        int required = Math.min(MINIMUM_RESPONDERS, servers.size());
        assertTrue(responders >= required, "Only " + responders + " of " + servers.size() + " " + network + " servers responded, at least " + required
                + " are needed to confirm the checkpoints. Unreachable: " + unreachable);
    }

    private void verifyAgainstServer(PublicElectrumServer publicServer, HeaderCheckpoints checkpoints) throws Exception {
        try(CloseableTransport transport = publicServer.getServer().getProtocol().getTransport(publicServer.getServer().getHostAndPort())) {
            transport.connect();
            Thread reader = new Thread(() -> {
                try {
                    ((TcpTransport)transport).readInputLoop();
                } catch(ServerException e) {
                    //Expected once the transport is closed
                }
            }, "CheckpointVerificationReadThread");
            reader.setDaemon(true);
            reader.start();

            ElectrumServerRpc rpc = new SimpleElectrumServerRpc();
            rpc.getServerVersion(transport, "Sparrow", ElectrumServer.SUPPORTED_VERSIONS);

            //The last pin must be buried, which is established by the server having the headers above it rather than by its announced tip
            int maxHeight = checkpoints.getMaxHeight();
            BlockHeaders buried = rpc.getBlockHeadersChunk(transport, maxHeight, CHECKPOINT_BURIAL_DEPTH + 1);
            assertEquals(CHECKPOINT_BURIAL_DEPTH + 1, buried.count, "The last pinned header at height " + maxHeight + " is not buried by " + CHECKPOINT_BURIAL_DEPTH + " blocks");

            for(int i = 0; i < PINS_VERIFIED; i++) {
                int pinnedHeight = maxHeight - (i * HeaderChainState.RETARGET_INTERVAL);
                if(pinnedHeight <= 0) {
                    break;
                }

                BlockHeaders pair = rpc.getBlockHeadersChunk(transport, pinnedHeight, 2);
                assertEquals(2, pair.count, "Server did not return the pinned header at height " + pinnedHeight + " and the one above it");
                BlockHeader pinned = header(pair, 0);
                BlockHeader above = header(pair, 1);

                assertTrue(pinned.verifyProofOfWork(), "Header at height " + pinnedHeight + " does not meet its claimed proof of work target");
                assertTrue(above.verifyProofOfWork(), "Header at height " + (pinnedHeight + 1) + " does not meet its claimed proof of work target");
                assertEquals(pinned.getHash(), above.getPrevBlockHash(), "Header at height " + (pinnedHeight + 1) + " does not link to the header below it");
                assertEquals(checkpoints.getHash(pinnedHeight), pinned.getHash(), "Pinned hash at height " + pinnedHeight);
                assertEquals(checkpoints.getBitsAfter(pinnedHeight), above.getDifficultyTarget(), "Pinned target following height " + pinnedHeight);
            }
        }
    }

    private BlockHeader header(BlockHeaders blockHeaders, int index) {
        return new BlockHeader(Utils.hexToBytes(blockHeaders.hex.substring(index * BlockHeaders.HEADER_HEX_LENGTH, (index + 1) * BlockHeaders.HEADER_HEX_LENGTH)));
    }

    @AfterEach
    public void tearDown() {
        Network.set(null);
    }
}
