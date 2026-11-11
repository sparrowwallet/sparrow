package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BitcoindTransportProxyTest {
    private static final String RPC_AUTH = "rpcuser:rpcpassword";
    private static final String RPC_RESPONSE = "{\"result\":{\"chain\":\"main\"},\"error\":null,\"id\":1}";
    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    private static Path tempHome;

    private static SocksProxy proxy;

    @BeforeAll
    public static void setUp() throws Exception {
        //Config.get() caches its instance for the life of the JVM, so keep these tests from loading the developer's real config and leaving it cached for those that follow
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
        proxy = new SocksProxy();
        Config.get().setProxyServer("127.0.0.1:" + proxy.getPort());
        Config.get().setUseProxy(true);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Config.get().setUseProxy(false);
        Config.get().setProxyServer(null);
        proxy.close();
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void reset() {
        proxy.connectTargets.clear();
        proxy.authReceived.clear();
        proxy.response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + RPC_RESPONSE.length() + "\r\n\r\n" + RPC_RESPONSE;
        proxy.sslContext = null;
    }

    @Test
    public void onionNodeIsReachedThroughProxy() {
        String host = onionHost('a');
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + host + ":8332"), "cormorant", RPC_AUTH);
        assertEquals(RPC_RESPONSE, pass(transport));
        assertEquals(List.of("DOMAIN:" + host + ":8332"), proxy.connectTargets);
        assertEquals(List.of(RPC_AUTH), proxy.authReceived);
    }

    @Test
    public void connectionIsReusedBetweenCalls() {
        String host = onionHost('i');
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + host + ":8332"), "cormorant", RPC_AUTH);
        for(int i = 0; i < 3; i++) {
            assertEquals(RPC_RESPONSE, pass(transport));
        }
        assertEquals(List.of("DOMAIN:" + host + ":8332"), proxy.connectTargets);
        assertEquals(List.of(RPC_AUTH, RPC_AUTH, RPC_AUTH), proxy.authReceived);
        transport.close();
    }

    @Test
    public void closedTransportRefusesCalls() {
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('j') + ":8332"), "cormorant", RPC_AUTH);
        transport.close();
        assertPassThrows(transport);
        assertTrue(proxy.connectTargets.isEmpty());
    }

    @Test
    public void undelimitedBodyIsReadToTheEnd() {
        proxy.response = "HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n\r\n" + RPC_RESPONSE;
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('b') + ":8332"), "cormorant", RPC_AUTH);
        assertEquals(RPC_RESPONSE, pass(transport));
    }

    @Test
    public void errorResponseIsReturnedWithoutTheNullResult() {
        String error = "{\"result\":null,\"error\":{\"code\":-32601,\"message\":\"Method not found\"},\"id\":1}";
        proxy.response = "HTTP/1.1 404 Not Found\r\nContent-Length: " + error.length() + "\r\n\r\n" + error;
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('c') + ":8332"), "cormorant", RPC_AUTH);
        assertEquals(error.replace("\"result\":null,", ""), pass(transport));
    }

    @Test
    public void authenticationFailureIsReported() {
        proxy.response = "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n";
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('d') + ":8332"), "cormorant", RPC_AUTH);
        IOException thrown = assertPassThrows(transport);
        assertTrue(thrown.getMessage().contains("authentication failed"), thrown.toString());
    }

    @Test
    public void closedConnectionPointsToRpcAllowIp() {
        proxy.response = "";
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('e') + ":8332"), "cormorant", RPC_AUTH);
        IOException thrown = assertPassThrows(transport);
        assertTrue(thrown.getMessage().contains("rpcallowip"), thrown.toString());
    }

    @Test
    public void truncatedBodyFails() {
        proxy.response = "HTTP/1.1 200 OK\r\nContent-Length: 1000\r\nConnection: close\r\n\r\n" + RPC_RESPONSE;
        BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('f') + ":8332"), "cormorant", RPC_AUTH);
        IOException thrown = assertPassThrows(transport);
        assertTrue(thrown.getMessage().contains("before the response") && !thrown.getMessage().contains("rpcallowip"), thrown.toString());
    }

    @Test
    public void proxiedRedirectIsNotFollowed() throws Exception {
        try(CountingServer redirectTarget = new CountingServer()) {
            proxy.response = "HTTP/1.0 305 Use Proxy\r\nLocation: http://127.0.0.1:" + redirectTarget.getPort() + "/\r\nContent-Length: 0\r\n\r\n";
            BitcoindTransport transport = new BitcoindTransport(new Server("http://" + onionHost('g') + ":8332"), "cormorant", RPC_AUTH);
            IOException thrown = assertPassThrows(transport);
            assertTrue(thrown.getMessage().contains("HTTP 305"), thrown.toString());
            assertEquals(0, redirectTarget.connections.get());
        }
    }

    @Test
    public void directRedirectIsNotFollowed() throws Exception {
        try(CountingServer redirectTarget = new CountingServer(); CountingServer node = new CountingServer()) {
            node.response = "HTTP/1.0 302 Found\r\nLocation: http://127.0.0.1:" + redirectTarget.getPort() + "/\r\nContent-Length: 0\r\n\r\n";
            BitcoindTransport transport = new BitcoindTransport(new Server("http://127.0.0.1:" + node.getPort()), "cormorant", RPC_AUTH);
            IOException thrown = assertPassThrows(transport);
            assertTrue(thrown.getMessage().contains("HTTP 302"), thrown.toString());
            assertEquals(0, redirectTarget.connections.get());
        }
    }

    @Test
    public void localNodeIsReachedDirectly() throws Exception {
        try(CountingServer node = new CountingServer()) {
            node.response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + RPC_RESPONSE.length() + "\r\nConnection: close\r\n\r\n" + RPC_RESPONSE;
            BitcoindTransport transport = new BitcoindTransport(new Server("http://127.0.0.1:" + node.getPort()), "cormorant", RPC_AUTH);
            assertEquals(RPC_RESPONSE, pass(transport));
            assertEquals(1, node.connections.get());
            assertTrue(proxy.connectTargets.isEmpty());
        }
    }

    @Test
    public void proxiedHttpsCertificateIsPinnedOnFirstUse() throws Exception {
        String host = onionHost('h');
        SSLContext first = createServerContext("first");
        SSLContext second = createServerContext("second");
        BitcoindTransport transport = new BitcoindTransport(new Server("https://" + host + ":8332"), "cormorant", RPC_AUTH);

        proxy.sslContext = first;
        assertEquals(RPC_RESPONSE, pass(transport));
        assertNotNull(Storage.getCertificateFile(host));
        assertEquals(List.of(RPC_AUTH), proxy.authReceived);

        assertEquals(RPC_RESPONSE, pass(transport));
        transport.close();

        proxy.authReceived.clear();
        proxy.sslContext = second;
        BitcoindTransport newTransport = new BitcoindTransport(new Server("https://" + host + ":8332"), "cormorant", RPC_AUTH);
        assertPassThrows(newTransport);
        assertTrue(proxy.authReceived.isEmpty());
    }

    private static String pass(BitcoindTransport transport) {
        return assertTimeoutPreemptively(Duration.ofSeconds(10), () -> transport.pass(request()));
    }

    private static IOException assertPassThrows(BitcoindTransport transport) {
        return assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(IOException.class, () -> transport.pass(request())));
    }

    private static String request() {
        return "{\"id\":1,\"method\":\"getblockchaininfo\",\"params\":[]}";
    }

    private static String onionHost(char c) {
        return "sparrowtestnode" + String.valueOf(c).repeat(40) + "d.onion";
    }

    private static SSLContext createServerContext(String name) throws Exception {
        File keyStoreFile = tempHome.resolve(name + ".p12").toFile();
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", name, "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=" + name, "-validity", "2",
                "-keystore", keyStoreFile.getAbsolutePath(), "-storetype", "PKCS12", "-storepass", STORE_PASSWORD).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0, "keytool failed");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try(FileInputStream fis = new FileInputStream(keyStoreFile)) {
            keyStore.load(fis, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, STORE_PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

    private static String readHttpRequest(InputStream is, List<String> authReceived) throws IOException {
        DataInputStream in = new DataInputStream(is);
        int contentLength = 0;
        String firstLine = null;
        for(String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
            if(firstLine == null) {
                firstLine = line;
            } else if(line.startsWith("Authorization: Basic ")) {
                authReceived.add(new String(Base64.getDecoder().decode(line.substring("Authorization: Basic ".length())), StandardCharsets.UTF_8));
            } else if(line.toLowerCase().startsWith("content-length: ")) {
                contentLength = Integer.parseInt(line.substring("content-length: ".length()));
            }
        }
        in.readNBytes(contentLength);
        return firstLine;
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

    //Stands in for a SOCKS5 proxy and the server it connects to: records the CONNECT target, then answers one HTTP request with the configured response
    private static class SocksProxy implements Closeable {
        private final List<String> connectTargets = new CopyOnWriteArrayList<>();
        private final List<String> authReceived = new CopyOnWriteArrayList<>();
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private volatile String response;
        private volatile SSLContext sslContext;

        SocksProxy() throws IOException {
            Thread acceptor = new Thread(() -> {
                try {
                    while(true) {
                        Socket socket = serverSocket.accept();
                        Thread handler = new Thread(() -> serve(socket));
                        handler.setDaemon(true);
                        handler.start();
                    }
                } catch(IOException e) {
                    //Closed at the end of the tests
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
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                //Greeting: version, number of methods, methods. Reply: no authentication.
                in.readUnsignedByte();
                in.readNBytes(in.readUnsignedByte());
                out.write(new byte[] {5, 0});

                //CONNECT: version, command, reserved, address type, address, port. Reply: succeeded.
                in.readNBytes(3);
                String address = switch(in.readUnsignedByte()) {
                    case 1 -> "IPV4:" + InetAddress.getByAddress(in.readNBytes(4)).getHostAddress();
                    case 3 -> "DOMAIN:" + new String(in.readNBytes(in.readUnsignedByte()), StandardCharsets.US_ASCII);
                    case 4 -> "IPV6:" + InetAddress.getByAddress(in.readNBytes(16)).getHostAddress();
                    default -> throw new IOException("Unknown SOCKS5 address type");
                };
                connectTargets.add(address + ":" + in.readUnsignedShort());
                out.write(new byte[] {5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
                out.flush();

                Socket stream = socket;
                SSLContext context = sslContext;
                if(context != null) {
                    SSLSocket sslSocket = (SSLSocket)context.getSocketFactory().createSocket(socket, socket.getInputStream(), true);
                    sslSocket.setUseClientMode(false);
                    sslSocket.startHandshake();
                    stream = sslSocket;
                }

                //Keeps the connection open for further requests where the response is delimited
                try(Socket far = stream) {
                    while(readHttpRequest(far.getInputStream(), authReceived) != null) {
                        String current = response;
                        far.getOutputStream().write(current.getBytes(StandardCharsets.US_ASCII));
                        far.getOutputStream().flush();
                        if(!current.contains("Content-Length") || current.contains("Connection: close")) {
                            break;
                        }
                    }
                }
            } catch(IOException e) {
                //A failed handshake or a client that closed the connection
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }

    //A plain HTTP server that counts the connections it receives and answers each with the configured response
    private static class CountingServer implements Closeable {
        private final AtomicInteger connections = new AtomicInteger();
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private volatile String response = "";

        CountingServer() throws IOException {
            Thread acceptor = new Thread(() -> {
                try {
                    while(true) {
                        try(Socket socket = serverSocket.accept()) {
                            connections.incrementAndGet();
                            readHttpRequest(socket.getInputStream(), new CopyOnWriteArrayList<>());
                            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                        }
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

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
