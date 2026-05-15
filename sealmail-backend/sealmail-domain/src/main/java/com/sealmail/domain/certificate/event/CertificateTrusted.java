package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;

public class CertificateTrusted extends DomainEvent {

    private final CertificateId certificateId;

    public CertificateTrusted(CertificateId certificateId) {
        this.certificateId = certificateId;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }
}
