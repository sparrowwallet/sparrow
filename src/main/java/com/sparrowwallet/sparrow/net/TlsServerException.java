package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;

public class TlsServerException extends ServerException {
    private final HostAndPort server;

    public TlsServerException(HostAndPort server) {
        this.server = server;
    }

    public TlsServerException(HostAndPort server, String message) {
        super(message);
        this.server = server;
    }

    public TlsServerException(HostAndPort server, Throwable cause) {
        this(server, getMessage(cause, server), cause);
    }

    public TlsServerException(HostAndPort server, String message, Throwable cause) {
        super(message, cause);
        this.server = server;
    }

    public HostAndPort getServer() {
        return server;
    }

    private static String getMessage(Throwable cause, HostAndPort server) {
        if(cause != null) {
            if(cause.getMessage().contains("PKIX path building failed")) {
                File configCrtFile = Config.get().getElectrumServerCert();
                File savedCrtFile = Storage.getCertificateFile(server.getHost());
                if(configCrtFile != null) {
                    return "Provided server certificate from " + server.getHost() + " did not match configured certificate at " + configCrtFile.getAbsolutePath();
                } else if(savedCrtFile != null) {
                    return "Provided server certificate from " + server.getHost() + " did not match previously saved certificate at " + savedCrtFile.getAbsolutePath();
                }

                return "Provided server certificate from " + server.getHost() + " was invalid: " + (cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());
            } else if(cause.getCause() instanceof CertificateNotYetValidException) {
                return cause.getMessage().replace("NotBefore:", "Certificate only valid from");
            } else if(cause.getCause() instanceof CertificateExpiredException) {
                return cause.getMessage().replace("NotAfter:", "Certificate expired at");
            }

            return cause.getMessage();
        }

        return "SSL Handshake Error";
    }
}
