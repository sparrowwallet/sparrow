package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import com.sparrowwallet.sparrow.io.Config;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TcpTransport implements Transport, Closeable {
    private static final Logger log = LoggerFactory.getLogger(TcpTransport.class);

    public static final int DEFAULT_PORT = 50001;
    private static final int[] READ_TIMEOUT_SECS = {3, 8, 16, 34};

    protected final HostAndPort server;
    protected final SocketFactory socketFactory;

    private Socket socket;

    private String response;

    private final ReentrantLock readLock = new ReentrantLock();
    private final Condition readingCondition = readLock.newCondition();

    private final ReentrantLock clientRequestLock = new ReentrantLock();
    private boolean running = false;
    private volatile boolean reading = true;
    private boolean firstRead = true;
    private int readTimeoutIndex;

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
    }

    @Override
    public @NotNull String pass(@NotNull String request) throws IOException {
        clientRequestLock.lock();
        try {
            Rpc sentRpc = request.startsWith("{") ? gson.fromJson(request, Rpc.class) : null;
            Rpc recvRpc;
            String recv;

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
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
        out.println(request);
        out.flush();
    }

    private String readResponse() throws IOException {
        try {
            if(!readLock.tryLock(READ_TIMEOUT_SECS[readTimeoutIndex], TimeUnit.SECONDS)) {
                readTimeoutIndex = Math.min(readTimeoutIndex + 1, READ_TIMEOUT_SECS.length - 1);
                log.debug("No response from server, setting read timeout to " + READ_TIMEOUT_SECS[readTimeoutIndex] + " secs");
                throw new IOException("No response from server");
            }
        } catch(InterruptedException e) {
            throw new IOException("Read thread interrupted");
        }

        if(readTimeoutIndex == READ_TIMEOUT_SECS.length - 1) {
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
                    //Restore interrupt status and continue
                    Thread.currentThread().interrupt();
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
            log.error("Error opening socket inputstream", e);
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
        String response = in.readLine();

        if(response == null) {
            throw new IOException("Could not connect to server at " + Config.get().getServerAddress());
        }

        return response;
    }

    public void connect() throws ServerException {
        try {
            socket = createSocket();
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
        return socket != null && running;
    }

    protected Socket createSocket() throws IOException {
        return socketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
    }

    @Override
    public void close() throws IOException {
        if(socket != null) {
            running = false;
            socket.close();
        }
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
