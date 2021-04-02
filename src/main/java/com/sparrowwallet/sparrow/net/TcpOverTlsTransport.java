package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

public class TcpOverTlsTransport extends TcpTransport {
    private static final Logger log = LoggerFactory.getLogger(TcpOverTlsTransport.class);

    public static final int DEFAULT_PORT = 50002;

    protected final SSLSocketFactory sslSocketFactory;

    public TcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException, CertificateException, KeyStoreException, IOException {
        super(server);

        TrustManager[] trustManagers = getTrustManagers(Storage.getCertificateFile(server.getHost()));

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());

        this.sslSocketFactory = sslContext.getSocketFactory();
    }

    public TcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server);

        TrustManager[] trustManagers = getTrustManagers(crtFile);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);

        sslSocketFactory = sslContext.getSocketFactory();
    }

    private TrustManager[] getTrustManagers(File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        if(crtFile == null) {
            return new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                            if(certs.length == 0) {
                                throw new CertificateException("No server certificate provided");
                            }

                            certs[0].checkValidity();
                        }
                    }
            };
        }

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));
        if(certificate instanceof X509Certificate) {
            try {
                X509Certificate x509Certificate = (X509Certificate)certificate;
                x509Certificate.checkValidity();
            } catch(CertificateException e) {
                crtFile.delete();
                return getTrustManagers(null);
            }
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("electrum-server", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        return trustManagerFactory.getTrustManagers();
    }

    protected Socket createSocket() throws IOException {
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
        startHandshake(sslSocket);
        return sslSocket;
    }

    protected void startHandshake(SSLSocket sslSocket) throws IOException {
        sslSocket.addHandshakeCompletedListener(event -> {
            if(Storage.getCertificateFile(server.getHost()) == null) {
                try {
                    Certificate[] certs = event.getPeerCertificates();
                    if(certs.length > 0) {
                        Storage.saveCertificate(server.getHost(), certs[0]);
                    }
                } catch(SSLPeerUnverifiedException e) {
                    log.warn("Attempting to retrieve certificate for unverified peer", e);
                }
            }
        });

        sslSocket.startHandshake();
    }
}
