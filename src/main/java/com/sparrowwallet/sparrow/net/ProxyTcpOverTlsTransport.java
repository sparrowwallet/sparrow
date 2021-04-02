package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;

import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class ProxyTcpOverTlsTransport extends TcpOverTlsTransport {
    public static final int DEFAULT_PROXY_PORT = 1080;

    private final HostAndPort proxy;

    public ProxyTcpOverTlsTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server);
        this.proxy = proxy;
    }

    public ProxyTcpOverTlsTransport(HostAndPort server, File crtFile, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server, crtFile);
        this.proxy = proxy;
    }

    @Override
    protected Socket createSocket() throws IOException {
        InetSocketAddress proxyAddr = new InetSocketAddress(proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT));
        Socket underlying = new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddr));
        underlying.connect(new InetSocketAddress(server.getHost(), server.getPortOrDefault(DEFAULT_PORT)));
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(underlying, proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT), true);
        startHandshake(sslSocket);

        return sslSocket;
    }
}
