package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class CertificateDeleted extends DomainEvent {

    private final CertificateId certificateId;
    private final EmailAddress owner;

    public CertificateDeleted(CertificateId certificateId, EmailAddress owner) {
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
