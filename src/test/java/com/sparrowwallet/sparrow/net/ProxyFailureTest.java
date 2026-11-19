package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.SparrowWallet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyFailureTest {
    @TempDir
    private static Path tempHome;

    @BeforeAll
    public static void setUp() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @Test
    public void unreachableServerIsNotAProxyFailure() throws Exception {
        try(ServerSocket proxy = new ServerSocket(0)) {
            Thread socks = new Thread(() -> {
                try(Socket socket = proxy.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    OutputStream out = socket.getOutputStream();
                    in.readByte();
                    in.readFully(new byte[in.readUnsignedByte()]);
                    out.write(new byte[] {5, 0});
                    in.readFully(new byte[4]);
                    in.readFully(new byte[in.readUnsignedByte() + 2]);
                    //Host unreachable for the server requested, one of the replies an unreachable onion service gives
                    out.write(new byte[] {5, 4, 0, 1, 0, 0, 0, 0, 0, 0});
                    out.flush();
                    InputStream ignored = socket.getInputStream();
                    ignored.read();
                } catch(Exception e) {
                    //closed at the end of the test
                }
            });
            socks.setDaemon(true);
            socks.start();

            TcpTransport transport = new TcpTransport(HostAndPort.fromParts("unreachable.onion", 50001), HostAndPort.fromParts("127.0.0.1", proxy.getLocalPort()));
            ServerException exception = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(ServerException.class, transport::connect));
            assertFalse(exception instanceof ProxyServerException);
        }
    }

    @Test
    public void proxyThatIsNotSocksIsAProxyFailure() throws Exception {
        try(ServerSocket proxy = new ServerSocket(0)) {
            Thread http = new Thread(() -> {
                try(Socket socket = proxy.accept()) {
                    socket.getInputStream().read(new byte[3]);
                    socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
                    socket.getOutputStream().flush();
                    socket.getInputStream().read();
                } catch(Exception e) {
                    //closed at the end of the test
                }
            });
            http.setDaemon(true);
            http.start();

            TcpTransport transport = new TcpTransport(HostAndPort.fromParts("server.onion", 50001), HostAndPort.fromParts("127.0.0.1", proxy.getLocalPort()));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(ProxyServerException.class, transport::connect));
        }
    }

    @Test
    public void proxyNotListeningIsAProxyFailure() throws Exception {
        int port;
        try(ServerSocket closed = new ServerSocket(0)) {
            port = closed.getLocalPort();
        }

        TcpTransport transport = new TcpTransport(HostAndPort.fromParts("server.onion", 50001), HostAndPort.fromParts("127.0.0.1", port));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(ProxyServerException.class, transport::connect));
    }

    @Test
    public void callsWithoutAConnectionDoNotCreateATransport() {
        CloseableTransport previous = ElectrumServer.transport;
        ElectrumServer.transport = null;
        try {
            assertThrows(ServerException.class, () -> new ElectrumServer().getServerVersion());
            assertNull(ElectrumServer.transport);
        } finally {
            ElectrumServer.transport = previous;
        }
    }

    @Test
    public void onionHostWithTrailingDotIsOnion() {
        assertTrue(Protocol.isOnionHost("example.onion."));
        assertTrue(Protocol.isOnionHost("EXAMPLE.ONION"));
        assertFalse(Protocol.isOnionHost("example.com."));
        assertFalse(Protocol.isOnionHost("example.onion.."));
    }

    @Test
    public void onionUrlWithTrailingDotIsNormalized() {
        assertEquals("tcp://example.onion:50001", Protocol.normalizeOnionUrl("tcp://example.onion.:50001"));
        assertEquals("ssl://example.onion", Protocol.normalizeOnionUrl("ssl://example.onion."));
        assertEquals("example.onion:50002:s", Protocol.normalizeOnionUrl("example.onion.:50002:s"));
        assertEquals("http://example.onion:8332/wallet/example.onion.", Protocol.normalizeOnionUrl("http://example.onion.:8332/wallet/example.onion."));
        assertEquals("tcp://example.com.:50001", Protocol.normalizeOnionUrl("tcp://example.com.:50001"));
        assertEquals("tcp://example.onion:50001", new com.sparrowwallet.sparrow.io.Server("tcp://example.onion.:50001").getUrl());
    }

    @Test
    public void proxyWithoutPortUsesDefaultPort() {
        TorHttpProxySupplier supplier = new TorHttpProxySupplier(null);
        supplier._setTorProxy(HostAndPort.fromHost("127.0.0.1"));
        assertEquals(HostAndPort.fromHost("127.0.0.1"), supplier.getTorProxy());
        assertEquals(ProxyTcpOverTlsTransport.DEFAULT_PROXY_PORT, supplier.getHttpProxy(null).orElseThrow().getPort());
    }

    @Test
    public void ipLiteralsAreReadAsInetAddressReadsThem() {
        assertTrue(IpAddressMatcher.isIpLiteral("192.168.1.10"));
        assertTrue(IpAddressMatcher.isIpLiteral("::1"));
        assertFalse(IpAddressMatcher.isIpLiteral("\uff11\uff19\uff12.\uff11\uff16\uff18.1.1"));
        assertFalse(IpAddressMatcher.isIpLiteral("192.168.1.1%eth0"));
        assertFalse(IpAddressMatcher.isIpLiteral("example.com"));
    }
}
