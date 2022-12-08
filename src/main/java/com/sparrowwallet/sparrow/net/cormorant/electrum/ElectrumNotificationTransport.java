package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.client.Transport;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ElectrumNotificationTransport implements Transport {
    private final Socket clientSocket;

    public ElectrumNotificationTransport(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public String pass(String request) throws IOException {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
        out.println(request);
        out.flush();

        return "{\"result\":{},\"error\":null,\"id\":1}";
    }
}
