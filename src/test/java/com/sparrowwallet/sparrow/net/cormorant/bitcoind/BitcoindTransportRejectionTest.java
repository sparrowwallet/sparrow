package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * From v32.1 Bitcoin Core closes the connection from a client outside rpcallowip instead of answering 403.
 */
public class BitcoindTransportRejectionTest {
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
    public void closedConnectionPointsToRpcAllowIp() throws Exception {
        assertRpcAllowIpHint(false);
    }

    @Test
    public void resetConnectionPointsToRpcAllowIp() throws Exception {
        assertRpcAllowIpHint(true);
    }

    private static void assertRpcAllowIpHint(boolean reset) throws Exception {
        try(ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                try {
                    while(!serverSocket.isClosed()) {
                        Socket socket = serverSocket.accept();
                        socket.setSoLinger(reset, 0);
                        socket.close();
                    }
                } catch(IOException e) {
                    //Server socket closed at the end of the test
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            BitcoindTransport transport = new BitcoindTransport(new Server("http://127.0.0.1:" + serverSocket.getLocalPort()), "test", "user:pass");
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                IOException thrown = assertThrows(IOException.class, () -> transport.pass("{\"id\":1,\"method\":\"getblockchaininfo\",\"params\":[]}"));
                assertTrue(thrown.getMessage().contains("rpcallowip"), "Expected the rpcallowip hint, got: " + thrown);
            });
        }
    }
}
