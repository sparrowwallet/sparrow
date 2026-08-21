package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the timeouts in BitcoindTransport#pass. Neither was set, and HttpURLConnection defaults both to
 * 0 (infinite), so a node that accepted a request and then never replied hung the calling thread forever.
 */
public class BitcoindTransportTimeoutTest {
    @TempDir
    private static Path tempHome;

    private static final int TEST_READ_TIMEOUT_MILLIS = 500;

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
    public void nonRespondingNodeTimesOutInsteadOfHangingForever() throws Exception {
        try(ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            startSilentServer(serverSocket);

            BitcoindTransport transport = new BitcoindTransport(new Server("http://127.0.0.1:" + serverSocket.getLocalPort()), "test", "user:pass");
            //Shorten the timeout this test must wait out - the production value is not under test
            transport.readTimeoutMillis = TEST_READ_TIMEOUT_MILLIS;

            //If this hangs instead of throwing, the regression is back
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                IOException thrown = assertThrows(IOException.class, () -> transport.pass(request("getblockchaininfo")));
                assertTrue(thrown instanceof SocketTimeoutException || thrown.getCause() instanceof SocketTimeoutException, "Expected a read timeout, got: " + thrown);
            });
        }
    }

    @Test
    public void readTimeoutIsNotStricterThanTheElectrumClientAllows() {
        //Bounding these calls more tightly than the Electrum client they serve would only fail the request sooner
        BitcoindTransport transport = new BitcoindTransport(new Server("http://127.0.0.1:1"), "test", "user:pass");
        assertTrue(transport.readTimeoutMillis >= Config.get().getMaxServerTimeout() * 1000);
    }

    @Test
    public void onlyRescanningMethodsAreExemptFromTheReadTimeout() {
        //Both rescan synchronously - importdescriptors from the descriptor birthday, loadwallet from the block the wallet was last unloaded at
        assertTrue(BitcoindTransport.isRescanningMethod(request("importdescriptors")));
        assertTrue(BitcoindTransport.isRescanningMethod(request("loadwallet")));
        assertFalse(BitcoindTransport.isRescanningMethod(request("getblockchaininfo")));
        assertFalse(BitcoindTransport.isRescanningMethod(request("listwallets")));
        //Only the method field is inspected, so a parameter value must not match
        assertFalse(BitcoindTransport.isRescanningMethod("{\"id\":1,\"method\":\"getwalletinfo\",\"params\":{\"note\":\"importdescriptors\"}}"));
        assertFalse(BitcoindTransport.isRescanningMethod("not valid json"));
        assertFalse(BitcoindTransport.isRescanningMethod(""));
    }

    /**
     * Accepts the request, then never replies - a node that has hung on the call.
     */
    private void startSilentServer(ServerSocket serverSocket) {
        Thread serverThread = new Thread(() -> {
            try(Socket accepted = serverSocket.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                while(in.readLine() != null) {
                    //Drain the request, then go silent
                }
            } catch(Exception e) {
                //Expected once the read timeout fires and the connection is torn down
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static String request(String method) {
        return "{\"id\":1,\"method\":\"" + method + "\",\"params\":{}}";
    }
}
