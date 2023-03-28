package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class TorTcpOverTlsTransport extends TcpOverTlsTransport {
    private static final Logger log = LoggerFactory.getLogger(TorTcpOverTlsTransport.class);

    public TorTcpOverTlsTransport(HostAndPort server) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server);
    }

    public TorTcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server, crtFile);
    }

    @Override
    protected void createSocket() throws IOException {
        TorTcpTransport torTcpTransport = new TorTcpTransport(server) {
            @Override
            protected int getDefaultPort() {
                return Protocol.SSL.getDefaultPort();
            }
        };
        torTcpTransport.createSocket();
        socket = torTcpTransport.socket;

        socket = sslSocketFactory.createSocket(socket, server.getHost(), server.getPortOrDefault(Protocol.SSL.getDefaultPort()), true);
        startHandshake((SSLSocket)socket);
    }
}
