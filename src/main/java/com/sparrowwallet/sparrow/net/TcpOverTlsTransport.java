package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class TcpOverTlsTransport extends TcpTransport {
    public static final int DEFAULT_PORT = 50002;

    protected final SSLSocketFactory sslSocketFactory;

    public TcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException {
        super(server);

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        this.sslSocketFactory = sslContext.getSocketFactory();
    }

    public TcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server);

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("electrumx", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        sslSocketFactory = sslContext.getSocketFactory();
    }

    protected Socket createSocket() throws IOException {
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
        sslSocket.startHandshake();

        return sslSocket;
    }
}
