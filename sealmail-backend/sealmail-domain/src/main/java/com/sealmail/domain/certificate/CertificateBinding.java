package com.sealmail.domain.certificate;

import com.sealmail.domain.certificate.event.CertificateBindingChanged;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.domain.shared.model.AggregateRoot;
import com.sealmail.domain.shared.model.EmailAddress;

import java.time.Instant;

public class CertificateBinding extends AggregateRoot<String> {

    private final String domain;
    private final EmailAddress owner;
    private CertificateId certificateId;
    private final CertificateBindingPurpose purpose;
    private boolean enabled;
    private final Instant createdAt;
    private Instant updatedAt;

    private CertificateBinding(String id,
                               String domain,
                               EmailAddress owner,
                               CertificateId certificateId,
                               CertificateBindingPurpose purpose,
                               boolean enabled,
                               Instant createdAt,
                               Instant updatedAt) {
        super(id);
        this.domain = DomainName.requireValid(domain);
        this.owner = requireOwner(owner);
        this.certificateId = requireCertificateId(certificateId);
        this.purpose = requirePurpose(purpose);
        this.enabled = enabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static CertificateBinding create(String id,
                                            EmailAddress owner,
                                            CertificateId certificateId,
                                            CertificateBindingPurpose purpose,
                                            boolean enabled) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Certificate binding id cannot be blank");
        }
        CertificateBinding binding = new CertificateBinding(
                id,
                owner.getDomain(),
                owner,
                certificateId,
                purpose,
                enabled,
                Instant.now(),
                Instant.now());
        binding.registerChanged("CREATE");
        return binding;
    }

    public static CertificateBinding restore(String id,
                                             String domain,
                                             EmailAddress owner,
                                             CertificateId certificateId,
                                             CertificateBindingPurpose purpose,
                                             boolean enabled,
                                             Instant createdAt,
                                             Instant updatedAt) {
        return new CertificateBinding(id, domain, owner, certificateId, purpose, enabled, createdAt, updatedAt);
    }

    public void rebind(CertificateId certificateId, boolean enabled) {
        this.certificateId = requireCertificateId(certificateId);
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        registerChanged("UPDATE");
    }

    public void enable() {
        if (!enabled) {
            enabled = true;
            updatedAt = Instant.now();
            registerChanged("ENABLE");
        }
    }

    public void disable() {
        if (enabled) {
            enabled = false;
            updatedAt = Instant.now();
            registerChanged("DISABLE");
        }
    }

    private void registerChanged(String operation) {
        registerEvent(new CertificateBindingChanged(
                getId(),
                domain,
                owner.getValue(),
                certificateId.getThumbprint(),
                purpose.name(),
                operation));
    }

    private static EmailAddress requireOwner(EmailAddress owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Certificate binding owner cannot be null");
        }
        return owner;
    }

    private static CertificateId requireCertificateId(CertificateId certificateId) {
        if (certificateId == null) {
            throw new IllegalArgumentException("Certificate binding certificate id cannot be null");
        }
        return certificateId;
    }

    private static CertificateBindingPurpose requirePurpose(CertificateBindingPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("Certificate binding purpose cannot be null");
        }
        return purpose;
    }

    public String getDomain() {
        return domain;
    }

    public EmailAddress getOwner() {
        return owner;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }

    public CertificateBindingPurpose getPurpose() {
        return purpose;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
