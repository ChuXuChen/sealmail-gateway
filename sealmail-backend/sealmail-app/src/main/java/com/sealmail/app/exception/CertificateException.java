package com.sealmail.app.exception;

public class CertificateException extends BusinessException {

    public CertificateException(String code, String message) {
        super(code, message);
    }

    public CertificateException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public static CertificateException invalidCertificate(String message) {
        return new CertificateException("CERT_INVALID", message);
    }

    public static CertificateException certificateExpired(String thumbprint) {
        return new CertificateException("CERT_EXPIRED", "Certificate has expired: " + thumbprint);
    }

    public static CertificateException revoked(String thumbprint) {
        return new CertificateException("CERT_REVOKED", "Certificate is revoked: " + thumbprint);
    }

    public static CertificateException importFailed(String message, Throwable cause) {
        return new CertificateException("CERT_IMPORT_FAILED", "Failed to import certificate: " + message, cause);
    }
}
