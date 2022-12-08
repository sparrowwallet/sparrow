package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

public class ImportDescriptor {
    private final String desc;
    private final Boolean active;
    private final Object timestamp;
    private final Boolean internal;

    public ImportDescriptor(String desc, Boolean active, Object timestamp, Boolean internal) {
        this.desc = desc;
        this.active = active;
        this.timestamp = timestamp;
        this.internal = internal;
    }

    public String getDesc() {
        return desc;
    }

    public Boolean getActive() {
        return active;
    }

    public Object getTimestamp() {
        return timestamp;
    }

    public Boolean getInternal() {
        return internal;
    }
}
