package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Config;
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
import java.util.Collection;
import java.util.List;

public class TcpOverTlsTransport extends TcpTransport {
    private static final Logger log = LoggerFactory.getLogger(TcpOverTlsTransport.class);
    public static final int PAD_TO_MULTIPLE_OF_BYTES = 96;
    private static final int GENERAL_NAME_DNS = 2;
    private static final int GENERAL_NAME_IP_ADDRESS = 7;

    protected final SSLSocketFactory sslSocketFactory;
    protected final boolean usingCaTrust;

    public TcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException, CertificateException, KeyStoreException, IOException {
        super(server);

        TrustManager[] trustManagers;
        if(Storage.getCaCertificateFile(server.getHost()) != null) {
            trustManagers = getCaTrustManagers();
            this.usingCaTrust = true;
        } else {
            trustManagers = getTrustManagers(Storage.getCertificateFile(server.getHost()), server.getHost());
            this.usingCaTrust = false;
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());

        this.sslSocketFactory = sslContext.getSocketFactory();
    }

    public TcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        super(server);

        this.usingCaTrust = false;
        TrustManager[] trustManagers = getTrustManagers(crtFile, server.getHost());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);

        sslSocketFactory = sslContext.getSocketFactory();
    }

    @Override
    protected void writeRequest(String request) throws IOException {
        int currentLength = request.length();
        int targetLength;
        if(currentLength % PAD_TO_MULTIPLE_OF_BYTES == 0) {
            targetLength = currentLength;
        } else {
            targetLength = ((currentLength / PAD_TO_MULTIPLE_OF_BYTES) + 1) * PAD_TO_MULTIPLE_OF_BYTES;
        }

        int paddingNeeded = targetLength - currentLength;
        if(paddingNeeded > 0) {
            super.writeRequest(request + " ".repeat(paddingNeeded));
        } else {
            super.writeRequest(request);
        }
    }

    public static TrustManager[] getTrustManagers(File crtFile, String host) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
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
                                if(Storage.getCertificateFile(host) == null) {
                                    throw new UnknownCertificateExpiredException(e.getMessage(), certs[0]);
                                }
                            }
                        }
                    }
            };
        }

        Certificate certificate;
        try(FileInputStream fis = new FileInputStream(crtFile)) {
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(fis);
        }
        if(certificate instanceof X509Certificate) {
            try {
                X509Certificate x509Certificate = (X509Certificate)certificate;
                x509Certificate.checkValidity();
            } catch(CertificateExpiredException e) {
                if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
                    crtFile.delete();
                    return getTrustManagers(null, host);
                }
                //Allow expired certificates for private servers where users may not have the expertise to renew
            } catch(CertificateNotYetValidException e) {
                //A certificate that was valid when saved or configured can only appear to be not yet valid if the system clock is incorrect - retain it
                log.warn("Certificate for " + host + " at " + crtFile.getAbsolutePath() + " is not yet valid, check the system clock is correct");
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
                        if(isCaValidated(certs, server)) {
                            Storage.saveCaCertificate(server.getHost(), certs[0]);
                        } else {
                            Storage.saveCertificate(server.getHost(), certs[0]);
                        }
                    }
                } catch(SSLPeerUnverifiedException e) {
                    log.warn("Attempting to retrieve certificate for unverified peer", e);
                }
            }
        });

        if(usingCaTrust && !Protocol.isOnionAddress(server)) {
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParameters);
        }

        sslSocket.startHandshake();
    }

    protected boolean shouldSaveCertificate() {
        return Storage.getCertificateFile(server.getHost()) == null && Storage.getCaCertificateFile(server.getHost()) == null;
    }

    private static TrustManager[] getCaTrustManagers() throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore)null);
        return tmf.getTrustManagers();
    }

    private static boolean isCaValidated(Certificate[] certs, HostAndPort server) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore)null);

            X509TrustManager defaultTm = null;
            for(TrustManager tm : tmf.getTrustManagers()) {
                if(tm instanceof X509TrustManager) {
                    defaultTm = (X509TrustManager)tm;
                    break;
                }
            }

            if(defaultTm == null) {
                return false;
            }

            X509Certificate[] x509Certs = new X509Certificate[certs.length];
            for(int i = 0; i < certs.length; i++) {
                x509Certs[i] = (X509Certificate)certs[i];
            }

            String authType = x509Certs[0].getPublicKey().getAlgorithm().equals("EC") ? "ECDHE_ECDSA" : "RSA";
            defaultTm.checkServerTrusted(x509Certs, authType);

            //Match the hostname as CA validated connections do, so that a certificate saved here can be validated when it is used
            return Protocol.isOnionAddress(server) || matchesHostname(x509Certs[0], server.getHost());
        } catch(Exception e) {
            return false;
        }
    }

    private static boolean matchesHostname(X509Certificate cert, String host) throws CertificateParsingException {
        Collection<List<?>> subjectAltNames = cert.getSubjectAlternativeNames();
        if(subjectAltNames == null) {
            return false;
        }

        for(List<?> subjectAltName : subjectAltNames) {
            if(subjectAltName.size() > 1 && subjectAltName.get(0) instanceof Integer type && subjectAltName.get(1) instanceof String name) {
                if((type == GENERAL_NAME_DNS && matchesDnsName(name, host)) || (type == GENERAL_NAME_IP_ADDRESS && name.equalsIgnoreCase(host))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean matchesDnsName(String dnsName, String host) {
        if(dnsName.startsWith("*.")) {
            int index = host.indexOf('.');
            return index > 0 && host.substring(index + 1).equalsIgnoreCase(dnsName.substring(2));
        }

        return dnsName.equalsIgnoreCase(host);
    }

    @Override
    protected int getDefaultPort() {
        return Protocol.SSL.getDefaultPort();
    }
}
