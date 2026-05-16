package com.sealmail.domain.certificate.event;

import com.sealmail.domain.shared.event.DomainEvent;

public class CertificateBindingChanged extends DomainEvent {

    private final String bindingId;
    private final String domain;
    private final String ownerEmail;
    private final String certificateId;
    private final String purpose;
    private final String operation;

    public CertificateBindingChanged(String bindingId,
                                     String domain,
                                     String ownerEmail,
                                     String certificateId,
                                     String purpose,
                                     String operation) {
        this.bindingId = bindingId;
        this.domain = domain;
        this.ownerEmail = ownerEmail;
        this.certificateId = certificateId;
        this.purpose = purpose;
        this.operation = operation;
    }

    public String getBindingId() {
        return bindingId;
    }

    public String getDomain() {
        return domain;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getOperation() {
        return operation;
    }
}
