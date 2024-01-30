package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

public class TcpOverTlsTransport extends TcpTransport {
    private static final Logger log = LoggerFactory.getLogger(TcpOverTlsTransport.class);

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

                            try {
                                certs[0].checkValidity();
                            } catch(CertificateExpiredException e) {
                                if(Storage.getCertificateFile(server.getHost()) == null) {
                                    throw new UnknownCertificateExpiredException(e.getMessage(), certs[0]);
                                }
                            }
                        }
                    }
            };
        }

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));
        if(certificate instanceof X509Certificate) {
            try {
                X509Certificate x509Certificate = (X509Certificate)certificate;
                x509Certificate.checkValidity();
            } catch(CertificateExpiredException e) {
                //Allow expired certificates so long as they have been previously used or explicitly approved
                //These will usually be self-signed certificates that users may not have the expertise to renew
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

    protected void createSocket() throws IOException {
        socket = sslSocketFactory.createSocket();
        socket.connect(new InetSocketAddress(server.getHost(), server.getPortOrDefault(Protocol.SSL.getDefaultPort())));
        startHandshake((SSLSocket)socket);
    }

    protected void startHandshake(SSLSocket sslSocket) throws IOException {
        sslSocket.addHandshakeCompletedListener(event -> {
            if(shouldSaveCertificate()) {
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

    protected boolean shouldSaveCertificate() {
        //Avoid saving the certificates for public servers - they change often, encourage approval complacency, and there is little a user can do to check
        for(PublicElectrumServer publicElectrumServer : PublicElectrumServer.getServers()) {
            if(publicElectrumServer.getServer().getHost().equals(server.getHost())) {
                return false;
            }
        }

        return Storage.getCertificateFile(server.getHost()) == null;
    }

    @Override
    protected int getDefaultPort() {
        return Protocol.SSL.getDefaultPort();
    }
}
