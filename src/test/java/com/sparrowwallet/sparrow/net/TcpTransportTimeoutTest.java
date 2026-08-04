package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Regression test for a hang in TcpTransport#readResponse: a server that accepts a
 * connection and a request but never replies used to block the caller forever.
 * <p>
 * readResponse() only time-bounded acquiring readLock via tryLock(). readLock is free
 * whenever the reader thread is blocked on the socket (it releases the lock while
 * awaiting/reading), so tryLock returned almost immediately regardless of the requested
 * timeout, and the actual wait for a response was an unbounded readingCondition.await()
 * right below it - which a non-responding server would never signal. The connection was
 * also never marked failed on that path, so it was never retried/reconnected either.
 */
public class TcpTransportTimeoutTest {

    @Test
    public void nonRespondingServerFailsWithinTimeoutInsteadOfHangingForever() throws Exception {
        try(ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptThread = new Thread(() -> {
                try(Socket accepted = serverSocket.accept()) {
                    //Accept the connection and read the request, then simply never reply.
                    BufferedReader in = new BufferedReader(new InputStreamReader(accepted.getInputStream()));
                    in.readLine();
                    Thread.sleep(Long.MAX_VALUE);
                } catch(Exception e) {
                    //Expected once the test tears down the server/transport
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            HostAndPort server = HostAndPort.fromParts("127.0.0.1", serverSocket.getLocalPort());
            TcpTransport transport = new TcpTransport(server);
            transport.connect();

            Thread readerThread = new Thread(() -> {
                try {
                    transport.readInputLoop();
                } catch(ServerException e) {
                    //Expected once the transport fails
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            try {
                //readTimeouts[0] defaults to 3s (BASE_READ_TIMEOUT_SECS). This must fail well
                //before it would have hung indefinitely under the old unbounded-await behaviour -
                //if this test hangs/times out instead of throwing, the regression is back.
                assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    assertThrows(IOException.class, () -> transport.pass("{\"id\":1,\"method\":\"server.ping\",\"params\":[]}")));

                assertFalse(transport.isConnected(), "Transport must be marked failed, not left hanging open, after a read timeout");
            } finally {
                transport.close();
            }
        }
    }
}
