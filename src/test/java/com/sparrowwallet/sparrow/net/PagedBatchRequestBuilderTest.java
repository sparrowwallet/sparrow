package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcBatchException;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for cross page partial success. The existing execute() rethrows a batch exception carrying only the failing page's results, so
 * earlier pages are discarded and later pages are never sent. Merkle proof verification reads a per transaction error as a refusal by that server,
 * so with execute() a single genuinely refused proof in a multi page request would have been reported as a refusal of every other transaction too.
 */
public class PagedBatchRequestBuilderTest {
    @TempDir
    private static Path tempHome;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    public static void setUp() {
        //Config.get() caches its instance statically for the life of the JVM, so keep these tests from loading the developer's real config
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void tolerantExecutionCoversEveryKeyAcrossPages() throws Exception {
        //Six requests over three pages, with one error in the first page and one in the last
        FakeBatchTransport transport = new FakeBatchTransport(Set.of("b", "f"), 0);
        Map<String, String> result = builder(transport).executeTolerant(1, "ERROR", e -> {});

        assertEquals(Set.of("a", "b", "c", "d", "e", "f"), result.keySet());
        assertEquals("result:a", result.get("a"));
        assertEquals("ERROR", result.get("b"));
        assertEquals("result:c", result.get("c"));
        assertEquals("result:e", result.get("e"));
        assertEquals("ERROR", result.get("f"));
        assertEquals(3, transport.requests.size(), "Every page must be sent, including those after a page that errored");
    }

    @Test
    public void tolerantExecutionKeepsEveryResultWhenNothingFails() throws Exception {
        FakeBatchTransport transport = new FakeBatchTransport(Set.of(), 0);
        Map<String, String> result = builder(transport).executeTolerant(1, "ERROR", e -> {});

        assertEquals(6, result.size());
        assertFalse(result.containsValue("ERROR"));
    }

    @Test
    public void existingExecutionDiscardsResultsOutsideTheFailingPage() {
        //The behaviour executeTolerant exists to avoid, asserted so that a change to either is a deliberate one
        FakeBatchTransport transport = new FakeBatchTransport(Set.of("b"), 0);
        JsonRpcBatchException e = assertThrows(JsonRpcBatchException.class, () -> builder(transport).execute(1));

        assertEquals(Set.of("a"), new HashSet<>(e.getSuccesses().keySet()));
        assertEquals(1, transport.requests.size(), "Pages after the failing one are never sent");
    }

    @Test
    public void batchErrorCheckSeesEveryFailingPage() throws Exception {
        FakeBatchTransport transport = new FakeBatchTransport(Set.of("b", "f"), 0);
        List<JsonRpcBatchException> seen = new ArrayList<>();
        builder(transport).executeTolerant(1, "ERROR", seen::add);

        assertEquals(2, seen.size());
        assertEquals(Set.of("b"), new HashSet<>(seen.get(0).getErrors().keySet()));
        assertEquals(Set.of("f"), new HashSet<>(seen.get(1).getErrors().keySet()));
    }

    @Test
    public void batchErrorCheckCanAbortTheWholeRequest() {
        //A method the server does not implement is a property of the server, so the caller stops rather than recording an error per key
        FakeBatchTransport transport = new FakeBatchTransport(Set.of("b"), ElectrumServerRpc.METHOD_NOT_FOUND);
        UnsupportedMethodException e = assertThrows(UnsupportedMethodException.class, () -> builder(transport).executeTolerant(1, "ERROR", batchException -> {
            if(ElectrumServerRpc.isMethodNotFound(batchException)) {
                throw new UnsupportedMethodException("test.method", batchException);
            }
        }));

        assertEquals("test.method", e.getMethod());
        assertEquals(1, transport.requests.size(), "No further pages are sent once the method is known to be unsupported");
    }

    @Test
    public void methodNotFoundRequiresEveryErrorToReportIt() {
        assertTrue(ElectrumServerRpc.isMethodNotFound(batchException(Map.of("a", ElectrumServerRpc.METHOD_NOT_FOUND, "b", ElectrumServerRpc.METHOD_NOT_FOUND))));
        assertFalse(ElectrumServerRpc.isMethodNotFound(batchException(Map.of("a", ElectrumServerRpc.METHOD_NOT_FOUND, "b", -32603))));
        assertFalse(ElectrumServerRpc.isMethodNotFound(batchException(Map.of("a", -32603))));
        //An empty error map would make allMatch vacuously true, so it is excluded
        assertFalse(ElectrumServerRpc.isMethodNotFound(batchException(Map.of())));
    }

    @Test
    public void pageSizeSurvivesTheBuilderCopies() throws Exception {
        FakeBatchTransport transport = new FakeBatchTransport(Set.of(), 0);
        PagedBatchRequestBuilder<String, String> batchRequest =
                (PagedBatchRequestBuilder<String, String>)PagedBatchRequestBuilder.create(transport, new AtomicLong()).pageSize(3).keysType(String.class).returnType(String.class);
        for(String id : List.of("a", "b", "c", "d", "e", "f")) {
            batchRequest.add(id, "test.method", id);
        }
        batchRequest.executeTolerant(1, "ERROR", e -> {});

        assertEquals(2, transport.requests.size(), "pageSize set before keysType and returnType must be carried onto the copies they return");
    }

    @SuppressWarnings("unchecked")
    private PagedBatchRequestBuilder<String, String> builder(Transport transport) {
        PagedBatchRequestBuilder<String, String> batchRequest =
                (PagedBatchRequestBuilder<String, String>)PagedBatchRequestBuilder.create(transport, new AtomicLong()).keysType(String.class).returnType(String.class).pageSize(2);
        for(String id : List.of("a", "b", "c", "d", "e", "f")) {
            batchRequest.add(id, "test.method", id);
        }

        return batchRequest;
    }

    private static JsonRpcBatchException batchException(Map<String, Integer> errorCodes) {
        Map<Object, com.github.arteam.simplejsonrpc.core.domain.ErrorMessage> errors = new java.util.HashMap<>();
        errorCodes.forEach((key, code) -> errors.put(key, new com.github.arteam.simplejsonrpc.core.domain.ErrorMessage(code, "error", null)));

        return new JsonRpcBatchException("test", Map.of(), errors);
    }

    /**
     * Answers a JSON-RPC batch request with a result for every request, except those whose single parameter is named as failing.
     */
    private static class FakeBatchTransport implements Transport {
        private final Set<String> failingParams;
        private final int errorCode;
        private final List<String> requests = new ArrayList<>();

        FakeBatchTransport(Set<String> failingParams, int errorCode) {
            this.failingParams = failingParams;
            this.errorCode = errorCode == 0 ? -32603 : errorCode;
        }

        @Override
        public String pass(String request) throws IOException {
            requests.add(request);
            ArrayNode responses = MAPPER.createArrayNode();
            for(JsonNode node : MAPPER.readTree(request)) {
                String param = node.get("params").get(0).asText();
                ObjectNode response = responses.addObject();
                response.put("jsonrpc", "2.0");
                response.set("id", node.get("id"));
                if(failingParams.contains(param)) {
                    ObjectNode error = response.putObject("error");
                    error.put("code", errorCode);
                    error.put("message", "failed for " + param);
                } else {
                    response.put("result", "result:" + param);
                }
            }

            return MAPPER.writeValueAsString(responses);
        }
    }
}
