package com.sparrowwallet.sparrow.joinstr;

import com.google.gson.Gson;

public class JoinstrEvent {

    public String type;
    public String id;
    public String public_key;
    public String denomination;
    public String peers;
    public String timeout;
    public String relay;
    public String fee_rate;
    public String transport;

    public JoinstrEvent(String eventContent) {

        Gson gson = new Gson();
        JoinstrEvent joinstrEvent = gson.fromJson(eventContent, JoinstrEvent.class);

        this.type = joinstrEvent.type;
        this.id = joinstrEvent.id;
        this.public_key = joinstrEvent.public_key;
        this.denomination = joinstrEvent.denomination;
        this.peers = joinstrEvent.peers;
        this.timeout = joinstrEvent.timeout;
        this.relay = joinstrEvent.relay;
        this.fee_rate = joinstrEvent.fee_rate;
        this.transport = joinstrEvent.transport;

    }

}
