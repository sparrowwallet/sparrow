package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Config;
import org.jetbrains.annotations.NotNull;

import javax.net.SocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class TcpTransport implements Transport, Closeable {
    public static final int DEFAULT_PORT = 50001;

    protected final HostAndPort server;
    protected final SocketFactory socketFactory;

    private Socket socket;

    private String response;

    private final ReentrantLock clientRequestLock = new ReentrantLock();
    private boolean running = false;
    private boolean reading = true;

    private final JsonRpcServer jsonRpcServer = new JsonRpcServer();
    private final SubscriptionService subscriptionService = new SubscriptionService();

    public TcpTransport(HostAndPort server) {
        this.server = server;
        this.socketFactory = SocketFactory.getDefault();
    }

    @Override
    public @NotNull String pass(@NotNull String request) throws IOException {
        clientRequestLock.lock();
        try {
            writeRequest(request);
            return readResponse();
        } finally {
            clientRequestLock.unlock();
        }
    }

    private void writeRequest(String request) throws IOException {
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
        out.println(request);
        out.flush();
    }

    private synchronized String readResponse() {
        while(reading) {
            try {
                wait();
            } catch(InterruptedException e) {
                //Restore interrupt status and continue
                Thread.currentThread().interrupt();
            }
        }

        reading = true;

        notifyAll();
        return response;
    }

    public synchronized void readInputLoop() throws ServerException {
        while(running) {
            try {
                String received = readInputStream();
                if(received.contains("method")) {
                    //Handle subscription notification
                    jsonRpcServer.handle(received, subscriptionService);
                } else {
                    //Handle client's response
                    response = received;
                    reading = false;
                    notifyAll();
                    wait();
                }
            } catch(InterruptedException e) {
                //Restore interrupt status and continue
                Thread.currentThread().interrupt();
            } catch(IOException e) {
                if(running) {
                    throw new ServerException(e);
                }
            }
        }
    }

    protected String readInputStream() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String response = in.readLine();

        if(response == null) {
            throw new IOException("Could not connect to server at " + Config.get().getElectrumServer());
        }

        return response;
    }

    public void connect() throws ServerException {
        try {
            socket = createSocket();
            running = true;
        } catch(IOException e) {
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
}
