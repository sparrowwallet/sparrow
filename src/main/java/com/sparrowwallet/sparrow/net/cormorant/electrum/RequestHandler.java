package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.net.cormorant.Cormorant;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.BitcoindClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RequestHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);
    private final Socket clientSocket;
    private final ElectrumServerService electrumServerService;
    private final JsonRpcServer rpcServer = new JsonRpcServer();
    private volatile PrintWriter out;

    private volatile boolean headersSubscribed;
    private final Set<String> scriptHashesSubscribed = ConcurrentHashMap.newKeySet();

    public RequestHandler(Socket clientSocket, BitcoindClient bitcoindClient, int electrumPort) {
        this.clientSocket = clientSocket;
        this.electrumServerService = new ElectrumServerService(bitcoindClient, this, electrumPort);
    }

    public void run() {
        try {
            InputStream input  = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

            OutputStream output = clientSocket.getOutputStream();
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)));

            Cormorant.getEventBus().register(this);
            try {
                while(true) {
                    String request = reader.readLine();
                    if(request == null) {
                        break;
                    }

                    send(rpcServer.handle(request, electrumServerService));
                }
            } finally {
                Cormorant.getEventBus().unregister(this);
            }
        } catch(IOException e) {
            log.error("Could not communicate with client socket", e);
        } finally {
            try {
                clientSocket.close();
            } catch(IOException e) {
                log.debug("Error closing client socket", e);
            }
        }
    }

    synchronized void send(String message) {
        out.println(message);
        out.flush();
    }

    public void setHeadersSubscribed(boolean headersSubscribed) {
        this.headersSubscribed = headersSubscribed;
    }

    public void subscribeScriptHash(String scriptHash) {
        scriptHashesSubscribed.add(scriptHash);
    }

    public boolean isScriptHashSubscribed(String scriptHash) {
        return scriptHashesSubscribed.contains(scriptHash);
    }

    @Subscribe
    public void newBlock(ElectrumBlockHeader electrumBlockHeader) {
        if(headersSubscribed) {
            ElectrumNotificationTransport electrumNotificationTransport = new ElectrumNotificationTransport(this);
            JsonRpcClient jsonRpcClient = new JsonRpcClient(electrumNotificationTransport);
            jsonRpcClient.onDemand(ElectrumNotificationService.class).notifyHeaders(electrumBlockHeader);
        }
    }

    @Subscribe
    public void scriptHashStatus(ScriptHashStatus scriptHashStatus) {
        if(isScriptHashSubscribed(scriptHashStatus.scriptHash())) {
            ElectrumNotificationTransport electrumNotificationTransport = new ElectrumNotificationTransport(this);
            JsonRpcClient jsonRpcClient = new JsonRpcClient(electrumNotificationTransport);
            jsonRpcClient.onDemand(ElectrumNotificationService.class).notifyScriptHash(scriptHashStatus.scriptHash(), scriptHashStatus.status());
        }
    }
}
