package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;

public class AliasAssigned extends DomainEvent {

    private final CertificateId certificateId;
    private final String alias;

    public AliasAssigned(CertificateId certificateId, String alias) {
        this.certificateId = certificateId;
        this.alias = alias;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public String getAlias() {
        return alias;
    }
}
