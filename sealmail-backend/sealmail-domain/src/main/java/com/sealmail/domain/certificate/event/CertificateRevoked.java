package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;

public class CertificateRevoked extends DomainEvent {

    private final CertificateId certificateId;
    private final String reason;

    public CertificateRevoked(CertificateId certificateId, String reason) {
        this.certificateId = certificateId;
        this.reason = reason;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public String getReason() {
        return reason;
    }
}
