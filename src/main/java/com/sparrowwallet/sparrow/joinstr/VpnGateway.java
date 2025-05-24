package com.sparrowwallet.sparrow.joinstr;

import java.io.Serializable;

public class VpnGateway implements Serializable {

    private String host;
    private String location;
    private String ipAddress;
    private int port;
    private String protocol;

    public VpnGateway() {}

    public VpnGateway(String host, String location, String ipAddress, int port, String protocol) {
        this.host = host;
        this.location = location;
        this.ipAddress = ipAddress;
        this.port = port;
        this.protocol = protocol;
    }

    public String getHost() { return host; };
    public void setHost(String host) { this.host = host; };

    public String getLocation() { return location; };
    public void setLocation(String location) { this.location = location; };

    public String getIpAddress() { return ipAddress; };
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; };

    public int getPort() { return port; };
    public void setPort(int port) { this.port = port; };

    public String getProtocol() { return protocol; };
    public void setProtocol(String protocol) { this.protocol = protocol; };

}
