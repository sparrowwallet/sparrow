package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.google.common.net.HostAndPort;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public enum Protocol {
    TCP {
        @Override
        public Transport getTransport(HostAndPort server) {
            if(isOnionAddress(server)) {
                return new TorTcpTransport(server);
            }

            return new TcpTransport(server);
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert) {
            return getTransport(server);
        }

        @Override
        public Transport getTransport(HostAndPort server, HostAndPort proxy) {
            //Avoid using a TorSocket if a proxy is specified, even if a .onion address
            return new TcpTransport(server, proxy);
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            return getTransport(server, proxy);
        }
    },
    SSL {
        @Override
        public Transport getTransport(HostAndPort server) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            if(isOnionAddress(server)) {
                return new TorTcpOverTlsTransport(server);
            }

            return new TcpOverTlsTransport(server);
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            if(isOnionAddress(server)) {
                return new TorTcpOverTlsTransport(server, serverCert);
            }

            return new TcpOverTlsTransport(server, serverCert);
        }

        @Override
        public Transport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, proxy);
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, serverCert, proxy);
        }
    },
    HTTP {
        @Override
        public Transport getTransport(HostAndPort server) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public Transport getTransport(HostAndPort server, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            throw new UnsupportedOperationException("No transport supported for HTTP");
        }
    };

    public abstract Transport getTransport(HostAndPort server) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract Transport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public abstract Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

    public HostAndPort getServerHostAndPort(String url) {
        return HostAndPort.fromString(url.substring(this.toUrlString().length()));
    }

    public String toUrlString() {
        return toString().toLowerCase() + "://";
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

    public static boolean isOnionAddress(HostAndPort server) {
        return server.getHost().toLowerCase().endsWith(TorService.TOR_ADDRESS_SUFFIX);
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
