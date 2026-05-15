package com.sealmail.domain.certificate.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.shared.event.DomainEvent;

import java.util.Set;

public class KeyUsagesUpdated extends DomainEvent {

    private final CertificateId certificateId;
    private final Set<KeyUsage> keyUsages;

    public KeyUsagesUpdated(CertificateId certificateId, Set<KeyUsage> keyUsages) {
        this.certificateId = certificateId;
        this.keyUsages = keyUsages;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public Set<KeyUsage> getKeyUsages() {
        return keyUsages;
    }
}
