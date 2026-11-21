package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the read timeout in TcpTransport#readResponse. Only acquiring readLock via tryLock() was
 * time bounded, but the read thread releases readLock while blocked on the socket, so tryLock returned almost
 * immediately regardless of the requested timeout and the actual wait for a response was an unbounded
 * readingCondition.await() that a server which stopped replying would never signal. Also covers reading response
 * lines across socket read timeouts and the response size limit.
 */
public class TcpTransportTimeoutTest {
    @TempDir
    private static Path tempHome;

    private static final int TEST_READ_TIMEOUT_SECS = 1;

    @BeforeAll
    public static void setUp() {
        //Config.get() caches its instance statically for the life of the JVM, so keep these tests from loading
        //the developer's real config and leaving it cached for every test class that runs afterwards
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void nonRespondingServerTimesOutInsteadOfHangingForever() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            //Held until teardown, so the server accepts the request and then simply never replies
            CountDownLatch firstReplyGate = new CountDownLatch(1);
            startServer(serverSocket, firstReplyGate);

            try {
                //Must fail within the configured read timeout - if this test hangs instead of throwing, the regression is back
                Duration limit = Duration.ofSeconds(transport.readTimeouts[0] + 5);
                assertTimeoutPreemptively(limit, () -> assertThrows(IOException.class, () -> transport.pass(pingRequest(1))));
                assertEquals(1, transport.getTimeoutCount(), "A request left unanswered must count as a timeout, which halves batch page sizes");
            } finally {
                //Let the server thread unwind and close the accepted socket rather than parking on the gate until JVM exit
                firstReplyGate.countDown();
                transport.close();
            }
        }
    }

    @Test
    public void lateRespondingServerRecoversOnTheNextRequest() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            CountDownLatch firstReplyGate = new CountDownLatch(1);
            startServer(serverSocket, firstReplyGate);

            try {
                //Every request is bounded so that a reintroduced hang fails this test rather than blocking the build
                Duration limit = Duration.ofSeconds(transport.readTimeouts[0] + 5);
                assertTimeoutPreemptively(limit, () -> assertThrows(IOException.class, () -> transport.pass(pingRequest(1))));

                //A read timeout must not fail the connection - the retry with a halved batch page size that follows is sent
                //over the same transport, discarding the stale response when it arrives
                assertTrue(transport.isConnected(), "Transport must remain connected after a read timeout");

                //Release the first reply only now, so it is guaranteed to arrive after the timeout rather than after a sleep
                firstReplyGate.countDown();
                assertEquals(pingResponse(2), assertTimeoutPreemptively(limit, () -> transport.pass(pingRequest(2))));
            } finally {
                //No-op unless an assertion above failed before the gate was released
                firstReplyGate.countDown();
                transport.close();
            }
        }
    }

    /**
     * A server that answers requests in turn, as electrs does, still has a request queued when the first read timeout passes, so sending it again only
     * doubles the work. The response must be waited for over the longer timeouts, and a slow response must not count as a timeout that halves page sizes.
     */
    @Test
    public void slowResponseIsWaitedForWithoutResending() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            Arrays.fill(transport.readTimeouts, 1, transport.readTimeouts.length, 3);

            AtomicInteger received = new AtomicInteger();
            Thread serverThread = new Thread(() -> {
                try(Socket accepted = serverSocket.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(accepted.getOutputStream(), StandardCharsets.UTF_8));
                    int id = 1;
                    while(in.readLine() != null) {
                        received.incrementAndGet();
                        if(id == 1) {
                            Thread.sleep(1500);
                        }
                        out.println(pingResponse(id++));
                        out.flush();
                    }
                } catch(Exception e) {
                    //Expected once the test tears down the server and transport
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            try {
                Duration limit = Duration.ofSeconds(transport.readTimeouts[1] + 5);
                assertEquals(pingResponse(1), assertTimeoutPreemptively(limit, () -> transport.pass(pingRequest(1))));
                assertEquals(1, received.get(), "The request must not be sent again while its response is awaited");
                assertEquals(0, transport.getTimeoutCount(), "A slow response must not count as a timeout");

                assertEquals(pingResponse(2), assertTimeoutPreemptively(limit, () -> transport.pass(pingRequest(2))));
            } finally {
                transport.close();
            }
        }
    }

    @Test
    public void requestIsPendingUntilItsResponseArrives() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            CountDownLatch firstReplyGate = new CountDownLatch(1);
            startServer(serverSocket, firstReplyGate);

            try {
                assertFalse(transport.isRequestPending());

                AtomicReference<Object> result = new AtomicReference<>();
                Thread requestThread = new Thread(() -> {
                    try {
                        result.set(transport.pass(pingRequest(1)));
                    } catch(IOException e) {
                        result.set(e);
                    }
                });
                requestThread.setDaemon(true);
                requestThread.start();

                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                    while(!transport.isRequestPending()) {
                        Thread.onSpinWait();
                    }
                });

                firstReplyGate.countDown();
                requestThread.join(Duration.ofSeconds(transport.readTimeouts[0] + 5));
                assertFalse(requestThread.isAlive(), "The request must complete once its reply is released");
                assertEquals(pingResponse(1), result.get(), "The request must return its reply rather than fail");
                assertFalse(transport.isRequestPending(), "No request may be pending once its response has arrived");
            } finally {
                firstReplyGate.countDown();
                transport.close();
            }
        }
    }

    @Test
    public void responseSplitBySocketReadTimeoutIsReassembled() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            transport.socket.setSoTimeout(100);
            String response = pingResponse(1);
            int split = response.length() / 2;

            //Pause between the two halves for several socket read timeouts, but well within the response read timeout
            startServer(serverSocket, out -> {
                out.write(response.substring(0, split).getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(500);
                out.write((response.substring(split) + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            });

            try {
                Duration limit = Duration.ofSeconds(transport.readTimeouts[0] + 5);
                assertEquals(response, assertTimeoutPreemptively(limit, () -> transport.pass(pingRequest(1))));
            } finally {
                transport.close();
            }
        }
    }

    @Test
    public void oversizedResponseFailsTheConnection() throws Exception {
        try(ServerSocket serverSocket = createServerSocket()) {
            TcpTransport transport = connectTransport(serverSocket);
            transport.maxResponseChars = 1024;

            CountDownLatch done = new CountDownLatch(1);
            startServer(serverSocket, out -> {
                byte[] chunk = new byte[4096];
                Arrays.fill(chunk, (byte)'a');
                out.write(chunk);
                out.flush();
                done.await();
            });

            try {
                Duration limit = Duration.ofSeconds(transport.readTimeouts[0] + 5);
                assertTimeoutPreemptively(limit, () -> assertThrows(IOException.class, () -> transport.pass(pingRequest(1))));
                assertFalse(transport.isConnected(), "Transport must fail the connection");
            } finally {
                done.countDown();
                transport.close();
            }
        }
    }

    private ServerSocket createServerSocket() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    private TcpTransport connectTransport(ServerSocket serverSocket) throws ServerException {
        TcpTransport transport = new TcpTransport(HostAndPort.fromParts("127.0.0.1", serverSocket.getLocalPort()));
        //Shorten the timeouts these tests must actually wait out - the escalation ladder itself is not under test
        Arrays.fill(transport.readTimeouts, TEST_READ_TIMEOUT_SECS);
        transport.connect();

        Thread readerThread = new Thread(() -> {
            try {
                transport.readInputLoop();
            } catch(ServerException e) {
                //Expected once the transport is closed
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        return transport;
    }

    /**
     * Replies to every request received in turn, withholding the reply to the first request until the given latch
     * is released. A latch that is never released is a server that accepts a request and never answers it.
     */
    private void startServer(ServerSocket serverSocket, CountDownLatch firstReplyGate) {
        Thread serverThread = new Thread(() -> {
            try(Socket accepted = serverSocket.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(accepted.getOutputStream(), StandardCharsets.UTF_8));

                int id = 1;
                while(in.readLine() != null) {
                    if(id == 1) {
                        firstReplyGate.await();
                    }
                    out.println(pingResponse(id++));
                    out.flush();
                }
            } catch(Exception e) {
                //Expected once the test tears down the server and transport
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Reads the first request and writes the reply with the given responder, then holds the connection open until the test tears it down.
     */
    private void startServer(ServerSocket serverSocket, Responder responder) {
        Thread serverThread = new Thread(() -> {
            try(Socket accepted = serverSocket.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8));
                in.readLine();
                responder.respond(accepted.getOutputStream());
                in.readLine();
            } catch(Exception e) {
                //Expected once the test tears down the server and transport
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private interface Responder {
        void respond(OutputStream out) throws Exception;
    }

    private static String pingRequest(int id) {
        return "{\"id\":" + id + ",\"method\":\"server.ping\",\"params\":[]}";
    }

    private static String pingResponse(int id) {
        return "{\"id\":" + id + ",\"result\":null}";
    }
}
