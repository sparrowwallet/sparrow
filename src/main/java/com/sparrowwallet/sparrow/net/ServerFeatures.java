package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerFeatures {
    //hosts is typed as Map<String, Object> rather than Map<String, HostInfo> because the wire shape
    //varies in practice: most servers return {host: {tcp_port: N, ssl_port: N}} per the Electrum spec,
    //but some (electrs) return {host: N} (a bare port number) or omit fields. Sparrow doesn't read this
    //(it's reserved for inbound deserialization compatibility); cormorant writes the spec-conformant
    //nested-map shape via a manually-constructed Map.
    public Map<String, Object> hosts;
    public String genesis_hash;
    public String hash_function;
    public String server_version;
    public String protocol_min;
    public String protocol_max;
    public Integer pruning;
    public List<Integer> silent_payments;

    public ServerFeatures() {}

    @Override
    public String toString() {
        return "ServerFeatures{server_version='" + server_version + "', protocol_min='" + protocol_min + "', protocol_max='" + protocol_max + "', silent_payments=" + silent_payments + '}';
    }
}
