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
import java.util.HashSet;
import java.util.Set;

public class RequestHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);
    private final Socket clientSocket;
    private final ElectrumServerService electrumServerService;
    private final JsonRpcServer rpcServer = new JsonRpcServer();

    private boolean headersSubscribed;
    private final Set<String> scriptHashesSubscribed = new HashSet<>();

    public RequestHandler(Socket clientSocket, BitcoindClient bitcoindClient) {
        this.clientSocket = clientSocket;
        this.electrumServerService = new ElectrumServerService(bitcoindClient, this);
    }

    public void run() {
        Cormorant.getEventBus().register(this);

        try {
            InputStream input  = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)));

            while(true) {
                String request = reader.readLine();
                if(request == null) {
                    break;
                }

                String response = rpcServer.handle(request, electrumServerService);
                out.println(response);
                out.flush();
            }
        } catch(IOException e) {
            log.error("Could not communicate with client socket", e);
        }

        Cormorant.getEventBus().unregister(this);
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
            ElectrumNotificationTransport electrumNotificationTransport = new ElectrumNotificationTransport(clientSocket);
            JsonRpcClient jsonRpcClient = new JsonRpcClient(electrumNotificationTransport);
            jsonRpcClient.onDemand(ElectrumNotificationService.class).notifyHeaders(electrumBlockHeader);
        }
    }

    @Subscribe
    public void scriptHashStatus(ScriptHashStatus scriptHashStatus) {
        if(isScriptHashSubscribed(scriptHashStatus.scriptHash())) {
            ElectrumNotificationTransport electrumNotificationTransport = new ElectrumNotificationTransport(clientSocket);
            JsonRpcClient jsonRpcClient = new JsonRpcClient(electrumNotificationTransport);
            jsonRpcClient.onDemand(ElectrumNotificationService.class).notifyScriptHash(scriptHashStatus.scriptHash(), scriptHashStatus.status());
        }
    }
}
