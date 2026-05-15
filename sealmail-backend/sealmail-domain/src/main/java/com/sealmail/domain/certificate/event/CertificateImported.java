package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class CertificateImported extends DomainEvent {

    private final CertificateId certificateId;
    private final EmailAddress owner;

    public CertificateImported(CertificateId certificateId, EmailAddress owner) {
        this.certificateId = certificateId;
        this.owner = owner;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public EmailAddress getOwner() {
        return owner;
    }
}
