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
            throw new UnsupportedOperationException("TCP protocol does not support proxying");
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
            throw new UnsupportedOperationException("TCP protocol does not support proxying");
        }
    },
    SSL {
        @Override
        public Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException {
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
        public Transport getTransport(HostAndPort server, HostAndPort proxy) throws NoSuchAlgorithmException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, proxy);
        }

        @Override
        public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            return new ProxyTcpOverTlsTransport(server, serverCert, proxy);
        }
    };

    public abstract Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException;

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

    public boolean isOnionAddress(HostAndPort server) {
        return server.getHost().toLowerCase().endsWith(".onion");
    }

    public static Protocol getProtocol(String url) {
        if(url.startsWith("tcp://")) {
            return TCP;
        }
        if(url.startsWith("ssl://")) {
            return SSL;
        }

        return null;
    }
}
