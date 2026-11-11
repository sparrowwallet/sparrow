package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.HashIndex;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.CoreAuthType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BitcoindClientSpentOutputsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setUp() {
        //Config.get() caches its instance for the life of the JVM, so keep these tests from loading the developer's real config and leaving it cached for those that follow
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void spentOutputsAreRequestedInBatches() throws Exception {
        //Two batches of 100 and 51, then getrawtransaction for the transaction the wallet does not hold
        assertSpentOutputs(310100, List.of(100, 51), 3);
    }

    @Test
    public void spentOutputsAreRequestedSinglyFromNodesBeforeV28() throws Exception {
        //One gettransaction for each, then getrawtransaction for the transaction the wallet does not hold
        assertSpentOutputs(270200, List.of(), 152);
    }

    @Test
    public void spentOutputsFetchedBeforeAFailedBatchAreKept() throws Exception {
        //The first batch, before the batch holding the failing transaction
        assertSpentOutputsBeforeFailureAreKept(310100, 100);
    }

    @Test
    public void spentOutputsFetchedBeforeAFailedRequestAreKeptFromNodesBeforeV28() throws Exception {
        //Every transaction requested before the failing one
        assertSpentOutputsBeforeFailureAreKept(270200, 120);
    }

    private void assertSpentOutputsBeforeFailureAreKept(int nodeVersion, int expectedKept) throws Exception {
        Map<String, String> walletTransactions = new LinkedHashMap<>();
        Map<String, Set<HashIndex>> expected = new HashMap<>();
        for(int i = 0; i < 150; i++) {
            addTransaction(i, walletTransactions, expected);
        }
        List<String> txids = new ArrayList<>(walletTransactions.keySet());

        try(FakeNode node = new FakeNode(walletTransactions, Map.of(), nodeVersion < 280000)) {
            node.failingTxid = txids.get(120);
            BitcoindClient client = createClient(node, nodeVersion);
            try {
                Map<String, Set<HashIndex>> spentOutputs = new HashMap<>();
                assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThrows(RuntimeException.class, () -> client.fetchSpentOutputs(txids, spentOutputs)));
                assertEquals(expectedKept, spentOutputs.size());
                for(String txid : txids.subList(0, expectedKept)) {
                    assertEquals(expected.get(txid), spentOutputs.get(txid));
                }
            } finally {
                client.stop();
            }
        }
    }

    private static BitcoindClient createClient(FakeNode node, int nodeVersion) {
        Config.get().setCoreServer(new Server("http://127.0.0.1:" + node.getPort()));
        Config.get().setCoreAuthType(CoreAuthType.USERPASS);
        Config.get().setCoreAuth("user:pass");
        BitcoindClient client = new BitcoindClient(false);
        client.networkInfo = new NetworkInfo(nodeVersion, "", true);
        return client;
    }

    private void assertSpentOutputs(int nodeVersion, List<Integer> expectedBatchSizes, int expectedRequests) throws Exception {
        Map<String, String> walletTransactions = new LinkedHashMap<>();
        Map<String, Set<HashIndex>> expected = new HashMap<>();
        for(int i = 0; i < 150; i++) {
            addTransaction(i, walletTransactions, expected);
        }
        //Held only in the node's transaction index, so that gettransaction fails and getrawtransaction answers
        Map<String, String> indexedTransactions = new HashMap<>();
        addTransaction(1000, indexedTransactions, expected);

        List<String> txids = new ArrayList<>(walletTransactions.keySet());
        txids.add(50, indexedTransactions.keySet().iterator().next());

        try(FakeNode node = new FakeNode(walletTransactions, indexedTransactions, nodeVersion < 280000)) {
            BitcoindClient client = createClient(node, nodeVersion);
            try {
                Map<String, Set<HashIndex>> spentOutputs = new HashMap<>();
                assertTimeoutPreemptively(Duration.ofSeconds(20), () -> client.fetchSpentOutputs(txids, spentOutputs));
                assertEquals(expected, spentOutputs);
                assertEquals(expectedBatchSizes, node.batchSizes);
                assertEquals(expectedRequests, node.requests.get());
            } finally {
                client.stop();
            }
        }
    }

    private static void addTransaction(int index, Map<String, String> transactions, Map<String, Set<HashIndex>> expected) {
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        Sha256Hash spentHash = Sha256Hash.of(("spent" + index).getBytes(StandardCharsets.UTF_8));
        transaction.addInput(spentHash, index % 4, new Script(new byte[0]));
        transaction.addOutput(1000L + index, new Script(new byte[] {0x51}));
        String txid = transaction.getTxId().toString();
        transactions.put(txid, Utils.bytesToHex(transaction.bitcoinSerialize()));
        expected.put(txid, Set.of(new HashIndex(spentHash, index % 4)));
    }

    //Answers gettransaction and getrawtransaction requests, singly or in a batch, on kept-alive connections. Before v28 Bitcoin Core answers in the JSON-RPC 1.0 form,
    //without a version and with both result and error, and with HTTP 500 for a failed request. From v28 it answers a JSON-RPC 2.0 request with HTTP 200 and one of the two.
    private static class FakeNode implements Closeable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final Map<String, String> walletTransactions;
        private final Map<String, String> indexedTransactions;
        private final boolean jsonRpc1Replies;
        //Any request naming this transaction is answered with an error page instead of JSON-RPC, as by a failing proxy in front of the node
        private volatile String failingTxid;
        private final AtomicInteger requests = new AtomicInteger();
        private final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

        FakeNode(Map<String, String> walletTransactions, Map<String, String> indexedTransactions, boolean jsonRpc1Replies) throws IOException {
            this.walletTransactions = walletTransactions;
            this.indexedTransactions = indexedTransactions;
            this.jsonRpc1Replies = jsonRpc1Replies;
            Thread acceptor = new Thread(() -> {
                try {
                    while(true) {
                        Socket socket = serverSocket.accept();
                        Thread handler = new Thread(() -> serve(socket));
                        handler.setDaemon(true);
                        handler.start();
                    }
                } catch(IOException e) {
                    //Closed at the end of the test
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void serve(Socket socket) {
            try(socket) {
                InputStream in = new BufferedInputStream(socket.getInputStream());
                while(true) {
                    int contentLength = -1;
                    for(String line = readLine(in); ; line = readLine(in)) {
                        if(line == null) {
                            return;
                        }
                        if(line.isEmpty()) {
                            break;
                        }
                        if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                            contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                        }
                    }
                    byte[] requestBytes = in.readNBytes(contentLength);
                    JsonNode request = MAPPER.readTree(requestBytes);
                    requests.incrementAndGet();

                    if(failingTxid != null && new String(requestBytes, StandardCharsets.UTF_8).contains(failingTxid)) {
                        String page = "Internal Server Error";
                        socket.getOutputStream().write(("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\nContent-Length: " + page.length() + "\r\n\r\n" + page)
                                .getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                        continue;
                    }

                    int status = 200;
                    String body;
                    if(request.isArray()) {
                        batchSizes.add(request.size());
                        ArrayNode responses = MAPPER.createArrayNode();
                        request.forEach(element -> responses.add(answer(element)));
                        body = MAPPER.writeValueAsString(responses);
                    } else {
                        ObjectNode response = answer(request);
                        status = jsonRpc1Replies && !response.get("error").isNull() ? 500 : 200;
                        body = MAPPER.writeValueAsString(response);
                    }

                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 " + status + " Status\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(bytes);
                    socket.getOutputStream().flush();
                }
            } catch(IOException e) {
                //Closed by the client
            }
        }

        private ObjectNode answer(JsonNode request) {
            ObjectNode response = MAPPER.createObjectNode();
            if(!jsonRpc1Replies) {
                response.put("jsonrpc", "2.0");
            }
            String method = request.get("method").asText();
            String txid = request.get("params").get(0).asText();
            if(method.equals("gettransaction") && walletTransactions.containsKey(txid)) {
                response.putObject("result").put("hex", walletTransactions.get(txid));
                if(jsonRpc1Replies) {
                    response.putNull("error");
                }
            } else if(method.equals("getrawtransaction") && indexedTransactions.containsKey(txid)) {
                response.put("result", indexedTransactions.get(txid));
                if(jsonRpc1Replies) {
                    response.putNull("error");
                }
            } else {
                if(jsonRpc1Replies) {
                    response.putNull("result");
                }
                ObjectNode error = response.putObject("error");
                error.put("code", -5);
                error.put("message", "Invalid or non-wallet transaction id");
            }
            response.set("id", request.get("id"));

            return response;
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder line = new StringBuilder();
            int b;
            while((b = in.read()) != -1 && b != '\n') {
                if(b != '\r') {
                    line.append((char)b);
                }
            }
            return b == -1 && line.isEmpty() ? null : line.toString();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
