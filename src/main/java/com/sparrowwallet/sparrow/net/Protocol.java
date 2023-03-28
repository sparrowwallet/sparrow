package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Server;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Locale;

public enum Protocol {
    TCP(50001) {
        @Override
        public CloseableTransport getTransport(HostAndPort server) {
            if(isOnionAddress(server)) {
                return new TorTcpTransport(server);
            }

            return new TcpTransport(server);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert) {
            return getTransport(server);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, HostAndPort proxy) {
            //Avoid using a TorSocket if a proxy is specified, even if a .onion address
            return new TcpTransport(server, proxy);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            return getTransport(server, proxy);
        }
    },
    SSL(50002) {
        @Override
        public CloseableTransport getTransport(HostAndPort server) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            if(isOnionAddress(server)) {
                return new TorTcpOverTlsTransport(server);
            }

            return new TcpOverTlsTransport(server);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            if(isOnionAddress(server)) {
                return new TorTcpOverTlsTransport(server, serverCert);
            }

            return new TcpOverTlsTransport(server, serverCert);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, proxy);
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, serverCert, proxy);
        }
    },
    HTTP(80) {
        @Override
        public CloseableTransport getTransport(HostAndPort server) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }
    },
    HTTPS(443) {
        @Override
        public CloseableTransport getTransport(HostAndPort server) {
            throw new UnsupportedOperationException("No transport supported for HTTPS");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert) {
            throw new UnsupportedOperationException("No transport supported for HTTPS");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTPS");
        }

        @Override
        public CloseableTransport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTPS");
        }
    };

    private final int defaultPort;

    Protocol(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public abstract CloseableTransport getTransport(HostAndPort server) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract CloseableTransport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract CloseableTransport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract CloseableTransport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public HostAndPort getServerHostAndPort(String url) {
        String lessProtocol = url.substring(this.toUrlString().length());
        int pathStart = lessProtocol.indexOf('/');
        return HostAndPort.fromString(pathStart < 0 ? lessProtocol : lessProtocol.substring(0, pathStart));
    }

    public String toUrlString() {
        return toString().toLowerCase(Locale.ROOT) + "://";
    }

    public String toUrlString(String host) {
        return toUrlString(HostAndPort.fromHost(host));
    }

    public String toUrlString(String host, int port) {
        return toUrlString(HostAndPort.fromParts(host, port));
    }

    public String toUrlString(HostAndPort hostAndPort) {
        return toUrlString() + hostAndPort.toString();
    }

    public static boolean isOnionHost(String host) {
        return host != null && host.toLowerCase(Locale.ROOT).endsWith(Tor.TOR_ADDRESS_SUFFIX);
    }

    public static boolean isOnionAddress(Server server) {
        if(server != null) {
            return isOnionAddress(server.getHostAndPort());
        }

        return false;
    }

    public static boolean isOnionAddress(HostAndPort server) {
        return isOnionHost(server.getHost());
    }

    public static boolean isOnionAddress(String address) {
        if(address != null) {
            Protocol protocol = Protocol.getProtocol(address);
            if(protocol != null) {
                return isOnionAddress(protocol.getServerHostAndPort(address));
            }
        }

        return false;
    }

    public static Protocol getProtocol(String url) {
        if(url.startsWith("tcp://")) {
            return TCP;
        }
        if(url.startsWith("ssl://")) {
            return SSL;
        }
        if(url.startsWith("http://")) {
            return HTTP;
        }
        if(url.startsWith("https://")) {
            return HTTPS;
        }

        return null;
    }

    public static String getHost(String url) {
        Protocol protocol = getProtocol(url);
        if(protocol != null) {
            return protocol.getServerHostAndPort(url).getHost();
        }

        return null;
    }
}
