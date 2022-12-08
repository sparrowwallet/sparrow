package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.sparrowwallet.sparrow.net.cormorant.bitcoind.BitcoindClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ElectrumServerRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ElectrumServerRunnable.class);

    private final BitcoindClient bitcoindClient;

    protected ServerSocket serverSocket = null;
    protected boolean stopped = false;
    protected Thread runningThread = null;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(10, r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    });

    public ElectrumServerRunnable(BitcoindClient bitcoindClient) {
        this.bitcoindClient = bitcoindClient;
        openServerSocket();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void run() {
        synchronized(this) {
            this.runningThread = Thread.currentThread();
        }
        while(!isStopped()) {
            Socket clientSocket;
            try {
                clientSocket = this.serverSocket.accept();
            } catch(IOException e) {
                if(isStopped()) {
                    break;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            RequestHandler requestHandler = new RequestHandler(clientSocket, bitcoindClient);
            this.threadPool.execute(requestHandler);
        }

        this.threadPool.shutdown();
    }

    private synchronized boolean isStopped() {
        return stopped;
    }

    public synchronized void stop() {
        stopped = true;
        try {
            serverSocket.close();
        } catch(IOException e) {
            throw new RuntimeException("Error closing server", e);
        }
    }

    private void openServerSocket() {
        try {
            serverSocket = new ServerSocket(0);
        } catch(IOException e) {
            throw new RuntimeException("Cannot open electrum server port", e);
        }
    }
}
