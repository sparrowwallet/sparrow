package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.base.Splitter;
import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import com.sparrowwallet.sparrow.io.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TcpTransport implements CloseableTransport, TimeoutCounter {
    private static final Logger log = LoggerFactory.getLogger(TcpTransport.class);

    public static final int DEFAULT_MAX_TIMEOUT = 34;
    private static final int[] BASE_READ_TIMEOUT_SECS = {3, 8, 16, DEFAULT_MAX_TIMEOUT};
    private static final int[] SLOW_READ_TIMEOUT_SECS = {34, 68, 124, 208};
    public static final long PER_REQUEST_READ_TIMEOUT_MILLIS = 50;
    public static final int SOCKET_READ_TIMEOUT_MILLIS = 5000;

    protected final HostAndPort server;
    protected final SocketFactory socketFactory;
    protected final int[] readTimeouts;

    protected Socket socket;

    private String response;

    private final CountDownLatch readReadySignal = new CountDownLatch(1);

    private final ReentrantLock readLock = new ReentrantLock();
    private final Condition readingCondition = readLock.newCondition();

    private final ReentrantLock clientRequestLock = new ReentrantLock();
    private boolean running = false;
    private volatile boolean reading = true;
    private boolean closed = false;
    private boolean firstRead = true;
    private int readTimeoutIndex;
    private int requestIdCount = 1;

    private final JsonRpcServer jsonRpcServer = new JsonRpcServer();
    private final SubscriptionService subscriptionService = new SubscriptionService();

    private Exception lastException;
    private final Gson gson = new Gson();

    public TcpTransport(HostAndPort server) {
        this(server, null);
    }

    public TcpTransport(HostAndPort server, HostAndPort proxy) {
        this.server = server;
        this.socketFactory = (proxy == null ? SocketFactory.getDefault() : new ProxySocketFactory(proxy));

        int[] timeouts = (Config.get().getServerType() == ServerType.BITCOIN_CORE && Protocol.isOnionAddress(Config.get().getCoreServer()) ?
                Arrays.copyOf(SLOW_READ_TIMEOUT_SECS, SLOW_READ_TIMEOUT_SECS.length) : Arrays.copyOf(BASE_READ_TIMEOUT_SECS, BASE_READ_TIMEOUT_SECS.length));
        if(Config.get().getMaxServerTimeout() > timeouts[timeouts.length - 1]) {
            timeouts[timeouts.length - 1] = Config.get().getMaxServerTimeout();
        }
        this.readTimeouts = timeouts;
    }

    @Override
    public @NotNull String pass(@NotNull String request) throws IOException {
        clientRequestLock.lock();
        try {
            Rpc sentRpc = request.startsWith("{") ? gson.fromJson(request, Rpc.class) : null;
            Rpc recvRpc;
            String recv;

            //Count number of requests in batched query to increase read timeout appropriately
            requestIdCount = Splitter.on("\"id\"").splitToList(request).size() - 1;
            writeRequest(request);
            do {
                recv = readResponse();
                recvRpc = recv.startsWith("{") ? gson.fromJson(response, Rpc.class) : null;
            } while(!Objects.equals(recvRpc, sentRpc));

            return recv;
        } finally {
            clientRequestLock.unlock();
        }
    }

    private void writeRequest(String request) throws IOException {
        if(log.isTraceEnabled()) {
            log.trace("Sending to electrum server at " + server + ": " + request);
        }

        if(socket == null) {
            throw new IllegalStateException("Socket connection has not been established.");
        }

        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
        out.println(request);
        out.flush();
    }

    private String readResponse() throws IOException {
        if(firstRead) {
            try {
                //Ensure read thread has started
                if(!readReadySignal.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Read thread did not start");
                }
            } catch(InterruptedException e) {
                throw new IOException("Read ready await interrupted");
            }
        }

        try {
            if(!readLock.tryLock((readTimeouts[readTimeoutIndex] * 1000L) + (requestIdCount * PER_REQUEST_READ_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS)) {
                readTimeoutIndex = Math.min(readTimeoutIndex + 1, readTimeouts.length - 1);
                log.warn("No response from server, setting read timeout to " + readTimeouts[readTimeoutIndex] + " secs");
                throw new IOException("No response from server");
            }
        } catch(InterruptedException e) {
            throw new IOException("Read thread interrupted");
        }

        if(readTimeoutIndex == readTimeouts.length - 1) {
            readTimeoutIndex--;
        }

        try {
            if(firstRead) {
                readingCondition.signal();
                firstRead = false;
            }

            while(reading) {
                try {
                    readingCondition.await();
                } catch(InterruptedException e) {
                    //Restore interrupt status and break
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if(lastException != null) {
                throw new IOException("Error reading response: " + lastException.getMessage(), lastException);
            }

            reading = true;

            readingCondition.signal();
            return response;
        } finally {
            readLock.unlock();
        }
    }

    public void readInputLoop() throws ServerException {
        readLock.lock();
        readReadySignal.countDown();

        try {
            try {
                //Don't start reading until first RPC request is sent
                readingCondition.await();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            while(running) {
                try {
                    String received = readInputStream(in);
                    if(received.contains("method") && !received.contains("error")) {
                        //Handle subscription notification
                        jsonRpcServer.handle(received, subscriptionService);
                    } else {
                        //Handle client's response
                        response = received;
                        reading = false;
                        readingCondition.signal();
                        readingCondition.await();
                    }
                } catch(InterruptedException e) {
                    //Restore interrupt status and continue
                    Thread.currentThread().interrupt();
                } catch(Exception e) {
                    log.trace("Connection error while reading", e);
                    if(running) {
                        lastException = e;
                        reading = false;
                        readingCondition.signal();
                        //Allow this thread to terminate as we will need to reconnect with a new transport anyway
                        running = false;
                    }
                }
            }
        } catch(IOException e) {
            if(!closed) {
                log.error("Error opening socket inputstream", e);
            }
            if(running) {
                lastException = e;
                reading = false;
                readingCondition.signal();
                //Allow this thread to terminate as we will need to reconnect with a new transport anyway
                running = false;
            }
        } finally {
            readLock.unlock();
        }
    }

    protected String readInputStream(BufferedReader in) throws IOException {
        String response = readLine(in);

        if(response == null) {
            throw new IOException("Could not connect to server" + (Config.get().hasServer() ? " at " + Config.get().getServer().getUrl() : ""));
        }

        return response;
    }

    private String readLine(BufferedReader in) throws IOException {
        while(!socket.isClosed()) {
            try {
                return in.readLine();
            } catch(SocketTimeoutException e) {
                //ignore and continue
            }
        }

        return null;
    }

    public void connect() throws ServerException {
        try {
            createSocket();
            log.debug("Created " + socket);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
            running = true;
        } catch(SSLHandshakeException e) {
            throw new TlsServerException(server, e);
        } catch(IOException e) {
            if(e.getStackTrace().length > 0 && e.getStackTrace()[0].getClassName().contains("SocksSocketImpl")) {
                throw new ProxyServerException(e);
            }

            throw new ServerException(e);
        }
    }

    public boolean isConnected() {
        return socket != null && running && !closed;
    }

    protected void createSocket() throws IOException {
        socket = socketFactory.createSocket();
        socket.connect(new InetSocketAddress(server.getHost(), server.getPortOrDefault(getDefaultPort())));
    }

    protected int getDefaultPort() {
        return Protocol.TCP.getDefaultPort();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        if(socket != null) {
            socket.close();
        }
        closed = true;
    }

    @Override
    public int getTimeoutCount() {
        return readTimeoutIndex;
    }

    private static class Rpc {
        public String id;

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            Rpc rpc = (Rpc) o;
            return Objects.equals(id, rpc.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
