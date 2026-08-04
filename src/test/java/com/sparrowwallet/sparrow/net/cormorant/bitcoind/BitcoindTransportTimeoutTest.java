package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.sparrowwallet.sparrow.io.Server;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for BitcoindTransport#pass never timing out. HttpURLConnection defaults
 * both connect and read timeout to 0 (infinite), and neither was ever set - so a bitcoind
 * that accepted a connection and a request but never replied hung the caller forever.
 * <p>
 * A short, injected timeout is used here (via the protected test hooks) instead of the real
 * 60s production default so the test stays fast; what's being verified is that pass()
 * actually respects whatever timeout is configured, not the specific default value.
 */
public class BitcoindTransportTimeoutTest {

    @Test
    public void methodClassificationIsNotFooledByParamsContainingTheName() {
        assertTrue(BitcoindTransport.isUnboundedReadTimeoutMethod(
                "{\"id\":1,\"method\":\"importdescriptors\",\"params\":{}}"));
        assertFalse(BitcoindTransport.isUnboundedReadTimeoutMethod(
                "{\"id\":1,\"method\":\"getblockchaininfo\",\"params\":{}}"));
        //A param value that happens to contain the long-running method's name must not
        //match - only the top-level "method" field should be inspected.
        assertFalse(BitcoindTransport.isUnboundedReadTimeoutMethod(
                "{\"id\":1,\"method\":\"getwalletinfo\",\"params\":{\"note\":\"importdescriptors\"}}"));
        assertFalse(BitcoindTransport.isUnboundedReadTimeoutMethod("not valid json"));
        assertFalse(BitcoindTransport.isUnboundedReadTimeoutMethod(""));
    }

    @Test
    public void nonRespondingServerFailsWithinTimeoutInsteadOfHangingForever() throws Exception {
        try(ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptThread = new Thread(() -> {
                try(Socket accepted = serverSocket.accept()) {
                    //Accept the connection and read the request, then simply never reply -
                    //simulating bitcoind accepting an RPC call and hanging.
                    BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream()));
                    while(in.readLine() != null) {
                        //drain headers/body, then go silent
                    }
                } catch(Exception e) {
                    //Expected once the test's read timeout fires and the connection is torn down
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            Server server = new Server("http://127.0.0.1:" + serverSocket.getLocalPort());
            TestableBitcoindTransport transport = new TestableBitcoindTransport(server, 500);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                IOException thrown = assertThrows(IOException.class,
                        () -> transport.pass("{\"id\":1,\"method\":\"getblockchaininfo\",\"params\":{}}"));
                assertTrue(thrown instanceof SocketTimeoutException || thrown.getCause() instanceof SocketTimeoutException,
                        "Expected a read timeout, got: " + thrown);
            });
        }
    }

    @Test
    public void unboundedMethodIsNotSubjectToTheReadTimeout() {
        TestableBitcoindTransport transport = new TestableBitcoindTransport(new Server("http://127.0.0.1:1"), 500);
        assertEquals(0, transport.getReadTimeoutMillis("{\"id\":1,\"method\":\"importdescriptors\",\"params\":{}}"));
        assertEquals(500, transport.getReadTimeoutMillis("{\"id\":1,\"method\":\"getblockchaininfo\",\"params\":{}}"));
    }

    private static class TestableBitcoindTransport extends BitcoindTransport {
        private final int testReadTimeoutMillis;

        TestableBitcoindTransport(Server server, int testReadTimeoutMillis) {
            super(server, "test", "user:pass");
            this.testReadTimeoutMillis = testReadTimeoutMillis;
        }

        @Override
        protected int getConnectTimeoutMillis() {
            return testReadTimeoutMillis;
        }

        @Override
        protected int getReadTimeoutMillis(String request) {
            return isUnboundedReadTimeoutMethod(request) ? 0 : testReadTimeoutMillis;
        }
    }
}
