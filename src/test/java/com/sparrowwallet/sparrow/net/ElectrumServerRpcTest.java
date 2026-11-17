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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /**
     * Where errors are tolerated, a path the server errored on is marked as such and every other path keeps its history, including those on pages
     * before and after the one with the error.
     */
    @Test
    public void tolerantHistoryCoversEveryPathAcrossPages() {
        List<String> paths = new ArrayList<>();
        for(int i = 0; i < PagedBatchRequestBuilder.DEFAULT_PAGE_SIZE * 2 + 50; i++) {
            paths.add("m/0/" + i);
        }
        Map<String, String> pathScriptHashes = new LinkedHashMap<>();
        paths.forEach(path -> pathScriptHashes.put(path, "sh:" + path));
        String failingPath = paths.get(PagedBatchRequestBuilder.DEFAULT_PAGE_SIZE + 25);
        ErroringTransport transport = new ErroringTransport(Set.of("sh:" + failingPath));

        Map<String, ScriptHashTx[]> result = new BatchedElectrumServerRpc(0, 25).getScriptHashHistory(transport, null, pathScriptHashes, false);

        assertEquals(new HashSet<>(paths), result.keySet());
        assertSame(ScriptHashTx.ERROR_TX, result.get(failingPath)[0]);
        assertEquals(800000, result.get(paths.getFirst())[0].height);
        assertEquals(800000, result.get(paths.getLast())[0].height);
    }

    /**
     * A transaction the server errored on is marked with the error sentinel, and every other transaction is returned, whichever page it was on.
     */
    @Test
    public void tolerantTransactionsCoverEveryTxidAcrossPages() {
        List<String> txids = new ArrayList<>();
        for(int i = 1; i <= PagedBatchRequestBuilder.DEFAULT_PAGE_SIZE * 2 + 50; i++) {
            txids.add(String.format("%064x", i));
        }
        String failingTxid = txids.get(PagedBatchRequestBuilder.DEFAULT_PAGE_SIZE + 25);
        ErroringTransport transport = new ErroringTransport(Set.of(failingTxid));

        Map<String, String> result = new BatchedElectrumServerRpc(0, 25).getTransactions(transport, null, new LinkedHashSet<>(txids));

        assertEquals(new HashSet<>(txids), result.keySet());
        assertEquals(Sha256Hash.ZERO_HASH.toString(), result.get(failingTxid));
        assertEquals("tx:" + txids.getFirst(), result.get(txids.getFirst()));
        assertEquals("tx:" + txids.getLast(), result.get(txids.getLast()));
    }

    /**
     * Answers history requests with a single confirmed entry and transaction requests with a placeholder, returning an error for the named parameters.
     */
    private static class ErroringTransport implements Transport {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final Set<String> failingParams;

        ErroringTransport(Set<String> failingParams) {
            this.failingParams = failingParams;
        }

        @Override
        public String pass(String request) throws java.io.IOException {
            ArrayNode responses = MAPPER.createArrayNode();
            for(JsonNode node : MAPPER.readTree(request)) {
                String param = node.get("params").get(0).asText();
                ObjectNode response = responses.addObject();
                response.put("jsonrpc", "2.0");
                response.set("id", node.get("id"));
                if(failingParams.contains(param)) {
                    ObjectNode error = response.putObject("error");
                    error.put("code", -32603);
                    error.put("message", "failed for " + param);
                } else if(node.get("method").asText().equals("blockchain.scripthash.get_history")) {
                    ObjectNode entry = response.putArray("result").addObject();
                    entry.put("height", 800000);
                    entry.put("tx_hash", String.format("%064x", 1));
                } else {
                    response.put("result", "tx:" + param);
                }
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
