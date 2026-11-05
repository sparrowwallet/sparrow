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

    /**
     * A timeout halves the page size, and a page size of one halved to zero, which Lists.partition rejects - failing every paged request for the rest of
     * the session on a configured maximum of one. One request is as small as a page can be, and a larger size is still halved.
     */
    @Test
    public void pageSizeHalvedAfterATimeoutIsNeverBelowOne() throws Exception {
        FakeBatchTransport transport = new TimingOutBatchTransport();
        builder(transport, 1).executeTolerant(1, "ERROR", e -> {});
        assertEquals(6, transport.requests.size());

        FakeBatchTransport halved = new TimingOutBatchTransport();
        builder(halved, 4).executeTolerant(1, "ERROR", e -> {});
        assertEquals(3, halved.requests.size());
    }

    @Test
    public void requestsSkippedForResponseSizeAreSentAgain() throws Exception {
        //A server answering two requests per batch, as a size limit reached on every second response would, leaves the page incomplete twice
        SizeLimitedBatchTransport transport = new SizeLimitedBatchTransport(2, Set.of());
        Map<String, String> result = builder(transport, 6).execute(1);

        assertEquals(6, result.size());
        for(String id : List.of("a", "b", "c", "d", "e", "f")) {
            assertEquals("result:" + id, result.get(id));
        }
        assertEquals(3, transport.requests.size());
        assertEquals(12, transport.ids.size());
        assertEquals(12, new HashSet<>(transport.ids).size(), "Each request sent again must carry a new id");
    }

    @Test
    public void requestsSkippedForResponseSizeAreSentAgainInTolerantExecution() throws Exception {
        SizeLimitedBatchTransport transport = new SizeLimitedBatchTransport(1, Set.of("e"));
        Map<String, String> result = builder(transport, 3).executeTolerant(1, "ERROR", e -> {});

        assertEquals(Set.of("a", "b", "c", "d", "e", "f"), result.keySet());
        assertEquals("ERROR", result.get("e"));
        assertEquals("result:f", result.get("f"));
    }

    @Test
    public void otherErrorsAreReportedAlongsideRequestsSentAgain() {
        //An error the server returns for an answered request is not resent, and is reported with the results of the requests that were resent
        SizeLimitedBatchTransport transport = new SizeLimitedBatchTransport(2, Set.of("b", "e"));
        JsonRpcBatchException e = assertThrows(JsonRpcBatchException.class, () -> builder(transport, 6).execute(1));

        assertEquals(Set.of("b", "e"), new HashSet<>(e.getErrors().keySet()));
        assertEquals(Set.of("a", "c", "d", "f"), new HashSet<>(e.getSuccesses().keySet()));
        assertEquals(3, transport.requests.size());
    }

    @Test
    public void pageWithEveryRequestSkippedIsNotSentAgain() {
        //A server answering nothing would never let a resend shrink, so the errors are returned rather than the page sent again
        SizeLimitedBatchTransport transport = new SizeLimitedBatchTransport(0, Set.of());
        JsonRpcBatchException e = assertThrows(JsonRpcBatchException.class, () -> builder(transport, 6).execute(1));

        assertEquals(6, e.getErrors().size());
        assertEquals(1, transport.requests.size());
    }

    @Test
    public void requestsSkippedByElectrumXAreSentAgain() throws Exception {
        //ElectrumX skips in the order responses complete, so the skipped requests can be anywhere in the page rather than at its end
        FirstSendErrorTransport transport = new FirstSendErrorTransport(Map.of("b", ELECTRUMX_TOO_LARGE, "d", ELECTRUMX_TOO_LARGE));
        Map<String, String> result = builder(transport, 6).execute(1);

        assertEquals(6, result.size());
        for(String id : List.of("a", "b", "c", "d", "e", "f")) {
            assertEquals("result:" + id, result.get(id));
        }
        assertEquals(2, transport.requests.size());
    }

    @Test
    public void otherInvalidRequestErrorsAreNotSentAgain() {
        FirstSendErrorTransport transport = new FirstSendErrorTransport(Map.of("c", new ErrorReply(ElectrumServerRpc.INVALID_REQUEST, "Invalid Request")));
        JsonRpcBatchException e = assertThrows(JsonRpcBatchException.class, () -> builder(transport, 6).execute(1));

        assertEquals(Set.of("c"), new HashSet<>(e.getErrors().keySet()));
        assertEquals(5, e.getSuccesses().size());
        assertEquals(1, transport.requests.size());
    }

    @Test
    public void pageWithEveryRequestSkippedByElectrumXIsNotSentAgain() {
        //A response over the limit on its own is refused however small the batch, so a page answering nothing is returned with its errors
        Map<String, ErrorReply> errors = new java.util.HashMap<>();
        for(String id : List.of("a", "b", "c", "d", "e", "f")) {
            errors.put(id, ELECTRUMX_TOO_LARGE);
        }
        FirstSendErrorTransport transport = new FirstSendErrorTransport(errors);
        JsonRpcBatchException e = assertThrows(JsonRpcBatchException.class, () -> builder(transport, 6).execute(1));

        assertEquals(6, e.getErrors().size());
        assertEquals(1, transport.requests.size());
    }

    @Test
    public void requestsSkippedByFrigateAndElectrumXAreBothSentAgain() throws Exception {
        FirstSendErrorTransport transport = new FirstSendErrorTransport(Map.of("b", ELECTRUMX_TOO_LARGE, "e", new ErrorReply(ElectrumServerRpc.RESPONSE_TOO_LARGE, "response too large")));
        Map<String, String> result = builder(transport, 6).execute(1);

        assertEquals(6, result.size());
        assertEquals("result:b", result.get("b"));
        assertEquals("result:e", result.get("e"));
        assertEquals(2, transport.requests.size());
    }

    private PagedBatchRequestBuilder<String, String> builder(Transport transport) {
        return builder(transport, 2);
    }

    @SuppressWarnings("unchecked")
    private PagedBatchRequestBuilder<String, String> builder(Transport transport, int pageSize) {
        PagedBatchRequestBuilder<String, String> batchRequest =
                (PagedBatchRequestBuilder<String, String>)PagedBatchRequestBuilder.create(transport, new AtomicLong()).keysType(String.class).returnType(String.class).pageSize(pageSize);
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

    /**
     * Answers the given number of requests in each batch and returns the response too large error for the rest, as a server limiting the size
     * of a batch response does, except those whose single parameter is named as failing.
     */
    private static class SizeLimitedBatchTransport implements Transport {
        private final int answered;
        private final Set<String> failingParams;
        private final List<String> requests = new ArrayList<>();
        private final List<Long> ids = new ArrayList<>();

        SizeLimitedBatchTransport(int answered, Set<String> failingParams) {
            this.answered = answered;
            this.failingParams = failingParams;
        }

        @Override
        public String pass(String request) throws IOException {
            requests.add(request);
            ArrayNode responses = MAPPER.createArrayNode();
            int index = 0;
            for(JsonNode node : MAPPER.readTree(request)) {
                ids.add(node.get("id").asLong());
                String param = node.get("params").get(0).asText();
                ObjectNode response = responses.addObject();
                response.put("jsonrpc", "2.0");
                response.set("id", node.get("id"));
                if(index++ >= answered) {
                    ObjectNode error = response.putObject("error");
                    error.put("code", ElectrumServerRpc.RESPONSE_TOO_LARGE);
                    error.put("message", "response too large");
                } else if(failingParams.contains(param)) {
                    ObjectNode error = response.putObject("error");
                    error.put("code", -32603);
                    error.put("message", "failed for " + param);
                } else {
                    response.put("result", "result:" + param);
                }
            }

            return MAPPER.writeValueAsString(responses);
        }
    }

    private static final ErrorReply ELECTRUMX_TOO_LARGE = new ErrorReply(ElectrumServerRpc.INVALID_REQUEST, "response too large (over 8,100,000 bytes");

    private record ErrorReply(int code, String message) {}

    /**
     * Returns the given error for a request the first time its single parameter is sent, and a result every time after, so that a request sent
     * again succeeds and one not sent again stays an error.
     */
    private static class FirstSendErrorTransport implements Transport {
        private final Map<String, ErrorReply> firstSendErrors;
        private final Set<String> sentParams = new HashSet<>();
        private final List<String> requests = new ArrayList<>();

        FirstSendErrorTransport(Map<String, ErrorReply> firstSendErrors) {
            this.firstSendErrors = firstSendErrors;
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
                ErrorReply reply = firstSendErrors.get(param);
                if(sentParams.add(param) && reply != null) {
                    ObjectNode error = response.putObject("error");
                    error.put("code", reply.code());
                    error.put("message", reply.message());
                } else {
                    response.put("result", "result:" + param);
                }
            }

            return MAPPER.writeValueAsString(responses);
        }
    }

    /**
     * A transport that has seen a timeout, which is what the page size is halved on.
     */
    private static class TimingOutBatchTransport extends FakeBatchTransport implements TimeoutCounter {
        TimingOutBatchTransport() {
            super(Set.of(), 0);
        }

        @Override
        public int getTimeoutCount() {
            return 1;
        }
    }
}
