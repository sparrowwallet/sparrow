package com.sparrowwallet.sparrow.net.http.client;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class HttpProxy {
    private final HttpProxyProtocol protocol;
    private final String host;
    private final int port;

    public HttpProxy(HttpProxyProtocol protocol, String host, int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    public static boolean validate(String proxy) {
        // check protocol
        String[] protocols = Arrays.stream(HttpProxyProtocol.values()).map(p -> p.name()).toArray(String[]::new);
        String regex = "^(" + StringUtils.join(protocols, "|").toLowerCase() + ")://(.+?):([0-9]+)";
        return proxy.trim().toLowerCase().matches(regex);
    }

    public HttpProxyProtocol getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return protocol + "://" + host + ":" + port;
    }
}
