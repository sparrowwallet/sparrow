package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The checks Electrum applies to a blockchain.block.headers response. Every failure is refusal class rather than a session failure, so the
 * header sync stops advancing on a server answering nonsense instead of feeding it to the chain state.
 */
public class ElectrumServerRpcTest {
    private static final int TIP = 900000;

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

    /**
     * A proof is small enough that its batch page is bounded by round trips rather than by bytes. Restoring a large wallet that has reused addresses
     * asks for a hundred thousand of them, and the generic page of 100 spends a round trip on each hundred - four minutes of a nineteen minute load,
     * measured, for about 130MB moved.
     */
    @Test
    public void pagesMerkleProofsByTheirOwnPageSize() {
        CountingTransport transport = new CountingTransport();
        List<BlockTransactionHash> references = new ArrayList<>();
        for(int i = 1; i <= BatchedElectrumServerRpc.MERKLE_BATCH_PAGE_SIZE + 1; i++) {
            references.add(new BlockTransaction(Sha256Hash.wrap(String.format("%064x", i)), 800000, null, 0L, null));
        }

        Map<String, TransactionMerkleProof> proofs = new BatchedElectrumServerRpc(0, 25).getTransactionMerkleProofs(transport, null, references);

        assertEquals(references.size(), proofs.size());
        assertEquals(2, transport.requests, "one page beyond the page size must be two requests, not " + (references.size() / 100 + 1));
    }

    /**
     * Answers every request in a batch with a proof, counting the batches it was sent.
     */
    private static class CountingTransport implements Transport {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private int requests;

        @Override
        public String pass(String request) throws java.io.IOException {
            requests++;
            ArrayNode responses = MAPPER.createArrayNode();
            for(JsonNode node : MAPPER.readTree(request)) {
                ObjectNode response = responses.addObject();
                response.put("jsonrpc", "2.0");
                response.set("id", node.get("id"));
                ObjectNode result = response.putObject("result");
                result.put("block_height", 800000);
                result.put("pos", 0);
                result.putArray("merkle");
            }

            return MAPPER.writeValueAsString(responses);
        }
    }

    @Test
    public void acceptsAFullResponse() {
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2016, 2016), 800000, 2016, TIP));
    }

    @Test
    public void acceptsAShortResponseThatReachesTheTip() {
        //The only reason a server may send fewer headers than asked for
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), TIP - 16, 2016, TIP));
    }

    @Test
    public void rejectsAShortResponseBelowTheTip() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), 800000, 2016, TIP));
    }

    @Test
    public void acceptsAShortResponseWhenTheTipIsUnknown() {
        //Before the application has a chain view there is nothing to judge the response against, so the caller's own linkage check decides
        assertDoesNotThrow(() -> ElectrumServerRpc.checkBlockHeaders(headers(17, 17, 2016), 800000, 2016, null));
    }

    @Test
    public void rejectsMoreHeadersThanRequested() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2017, 2017, 2016), 800000, 2016, TIP));
    }

    @Test
    public void rejectsAHexLengthInconsistentWithTheCount() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2015, 2016), 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2017, 2016), 800000, 2016, TIP));
    }

    @Test
    public void rejectsAServerThatCannotReturnAWholeDifficultyPeriod() {
        //A period must always fit one call, or the sync cannot advance across a retarget
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, 2016, HeaderChainState.RETARGET_INTERVAL - 1), 800000, 2016, TIP));
    }

    @Test
    public void rejectsMalformedResponses() {
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(null, 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(2016, -1, 2016), 800000, 2016, TIP));
        assertThrows(VerificationException.class, () -> ElectrumServerRpc.checkBlockHeaders(headers(-1, 0, 2016), 800000, 2016, TIP));
    }

    /**
     * @param count       the count the server reports
     * @param hexHeaders  the number of headers actually present in the hex
     * @param max         the maximum number of headers the server reports it will return
     */
    private static BlockHeaders headers(int count, int hexHeaders, int max) {
        BlockHeaders blockHeaders = new BlockHeaders();
        blockHeaders.count = count;
        blockHeaders.hex = hexHeaders < 0 ? null : "0".repeat(hexHeaders * BlockHeaders.HEADER_HEX_LENGTH);
        blockHeaders.max = max;

        return blockHeaders;
    }
}
