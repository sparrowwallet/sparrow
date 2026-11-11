package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.tern.http.client.HttpUsage;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class HttpClientServiceTest {
    private static final String BODY = "{\"value\":1}";

    @Test
    public void clientRequestedWhileChangingProxyUsesTheNewProxy() throws Exception {
        try(SocksProxy proxy = new SocksProxy()) {
            RequestingWhileStoppingService service = new RequestingWhileStoppingService();
            try {
                service.requestDuringStop = true;
                service.setTorProxy(HostAndPort.fromParts("127.0.0.1", proxy.getPort()));
                assertFalse(service.requestDuringStop, "The client was not requested during stop()");

                Map<?, ?> result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> service.requestJson("http://httpclientservice.invalid:8080/", Map.class, null));
                assertEquals(1, result.get("value"));
                assertEquals(List.of("httpclientservice.invalid:8080"), proxy.connectTargets);
            } finally {
                service.stop();
            }
        }
    }

    //Requests a client from another thread as the clients are stopped, as a concurrent request arriving while the proxy is changed would
    private static class RequestingWhileStoppingService extends HttpClientService {
        private volatile boolean requestDuringStop;

        RequestingWhileStoppingService() {
            super(null);
        }

        @Override
        public synchronized void stop() {
            super.stop();
            if(requestDuringStop) {
                requestDuringStop = false;
                Thread requester = new Thread(() -> getHttpClient(HttpUsage.DEFAULT));
                requester.setDaemon(true);
                requester.start();
                try {
                    //Waits for the client to be created where nothing prevents it, but not indefinitely where the lock held here does
                    requester.join(500);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    //Stands in for a SOCKS4a proxy and the server behind it: records each CONNECT target, then answers the request with a JSON body
    private static class SocksProxy implements Closeable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final List<String> connectTargets = new CopyOnWriteArrayList<>();

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
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();

                //SOCKS4a CONNECT: version, command, port, address 0.0.0.x, user id, hostname. Reply: granted.
                in.readUnsignedByte();
                in.readUnsignedByte();
                int port = in.readUnsignedShort();
                byte[] address = in.readNBytes(4);
                readNullTerminated(in);
                String host = address[0] == 0 && address[1] == 0 && address[2] == 0 && address[3] != 0 ? readNullTerminated(in) : InetAddress.getByAddress(address).getHostAddress();
                connectTargets.add(host + ":" + port);
                out.write(new byte[] {0, 0x5A, 0, 0, 0, 0, 0, 0});

                for(String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                    //Request headers, no body
                }
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + BODY.length() + "\r\nConnection: close\r\n\r\n" + BODY)
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch(IOException e) {
                //Closed by the client
            }
        }

        private static String readNullTerminated(InputStream in) throws IOException {
            StringBuilder value = new StringBuilder();
            for(int b = in.read(); b > 0; b = in.read()) {
                value.append((char)b);
            }
            return value.toString();
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
