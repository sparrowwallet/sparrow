package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListDescriptorResult(String desc, long timestamp, boolean active, boolean internal, List<Integer> range) {
    public Date getScanDate() {
        return timestamp > 0 ? new Date(timestamp * 1000) : null;
    }
}
