package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.AppServices;
import org.berndpruenster.netlayer.tor.*;

import java.io.*;
import java.net.Socket;

public class TorTcpTransport extends TcpTransport {
    public static final String TOR_DIR_PREFIX = "tor";

    public TorTcpTransport(HostAndPort server) {
        super(server);
    }

    public TorTcpTransport(HostAndPort server, HostAndPort proxy) {
        super(server, proxy);
    }

    @Override
    protected void createSocket() throws IOException {
        if(!AppServices.isTorRunning()) {
            throw new IllegalStateException("Can't create Tor socket, Tor is not running");
        }

        socket = new TorSocket(server.getHost(), server.getPort(), "sparrow");
    }
}
