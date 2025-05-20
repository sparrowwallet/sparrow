package com.sparrowwallet.sparrow.joinstr;

public class JoinstrPool {

    private final String relay;
    private final Integer port;
    private final String pubkey;
    private final Double denomination;

    public JoinstrPool(String relay_, Integer port_, String pubkey_, Double denomination_) {

        relay = relay_;
        port = port_;
        pubkey = pubkey_;
        denomination = denomination_;

    }

    public String getRelay() {
        return relay;
    }

    public Integer getPort() {
        return port;
    }

    public String getPubkey() {
        return pubkey;
    }

    public Double getDenomination() {
        return denomination;
    }

}
