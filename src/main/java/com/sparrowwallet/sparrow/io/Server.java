package com.sparrowwallet.sparrow.io;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.net.Protocol;

import java.util.Arrays;

public class Server {
    private final String url;
    private final String alias;

    public Server(String url) {
        this(url, null);
    }

    public Server(String url, String alias) {
        if(url == null) {
            throw new IllegalArgumentException("Url cannot be null");
        }

        if(url.isEmpty()) {
            throw new IllegalArgumentException("Url cannot be empty");
        }

        if(Protocol.getProtocol(url) == null) {
            throw new IllegalArgumentException("Unknown protocol for url " + url + ", must be one of " + Arrays.toString(Protocol.values()));
        }

        if(Protocol.getHost(url) == null) {
            throw new IllegalArgumentException("Cannot determine host for url " + url);
        }

        if(alias != null && alias.isEmpty()) {
            throw new IllegalArgumentException("Server alias cannot be an empty string");
        }

        this.url = url;
        this.alias = alias;
    }

    public String getUrl() {
        return url;
    }

    public Protocol getProtocol() {
        return Protocol.getProtocol(url);
    }

    public HostAndPort getHostAndPort() {
        return getProtocol().getServerHostAndPort(url);
    }

    public String getHost() {
        return getHostAndPort().getHost();
    }

    public boolean isOnionAddress() {
        return Protocol.isOnionAddress(getHostAndPort());
    }

    public String getAlias() {
        return alias;
    }

    public String getDisplayName() {
        return alias == null ? url : alias;
    }

    public String toString() {
        return url + (alias == null ? "" : "|" + alias);
    }

    public boolean portEquals(String port) {
        if(port == null) {
            return !getHostAndPort().hasPort();
        }

        return port.equals(getHostAndPort().hasPort() ? Integer.toString(getHostAndPort().getPort()) : "");
    }

    public static Server fromString(String server) {
        String[] parts = server.split("\\|");
        if(parts.length >= 2) {
            return new Server(parts[0], parts[1]);
        }

        return new Server(parts[0], null);
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }

        Server server = (Server)o;
        return url.equals(server.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }
}
