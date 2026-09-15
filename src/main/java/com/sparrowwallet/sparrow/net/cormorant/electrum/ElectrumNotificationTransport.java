package com.sparrowwallet.sparrow.net.cormorant.electrum;

import com.github.arteam.simplejsonrpc.client.Transport;

public class ElectrumNotificationTransport implements Transport {
    private final RequestHandler requestHandler;

    public ElectrumNotificationTransport(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    public String pass(String request) {
        requestHandler.send(request);

        return "{\"result\":{},\"error\":null,\"id\":1}";
    }
}
