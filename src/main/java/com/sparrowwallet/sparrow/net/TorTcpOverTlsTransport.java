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

    public TorTcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException {
        super(server);
    }

    public TorTcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server, crtFile);
    }

    @Override
    protected Socket createSocket() throws IOException {
        TorTcpTransport torTcpTransport = new TorTcpTransport(server);
        Socket socket = torTcpTransport.createSocket();

        try {
            Field socketField = socket.getClass().getDeclaredField("socket");
            socketField.setAccessible(true);
            Socket innerSocket = (Socket)socketField.get(socket);
            Field connectedField = innerSocket.getClass().getSuperclass().getDeclaredField("connected");
            connectedField.setAccessible(true);
            connectedField.set(innerSocket, true);
        } catch(Exception e) {
            log.error("Could not set socket connected status", e);
        }

        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, server.getHost(), server.getPortOrDefault(DEFAULT_PORT), true);
        sslSocket.startHandshake();

        return sslSocket;
    }
}
