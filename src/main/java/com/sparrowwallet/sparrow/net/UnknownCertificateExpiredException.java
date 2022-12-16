package com.sparrowwallet.sparrow.net;

import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;

public class UnknownCertificateExpiredException extends CertificateExpiredException {
    private final Certificate certificate;

    public UnknownCertificateExpiredException(String message, Certificate certificate) {
        super(message);
        this.certificate = certificate;
    }

    public Certificate getCertificate() {
        return certificate;
    }
}
