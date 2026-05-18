package com.sealmail.domain.key;

import com.sealmail.domain.shared.model.EmailAddress;

import java.time.Instant;

public class KeyRecord {

    public static final String MANAGED_KEY_REF_PREFIX = "managed-key:";

    private final String keyId;
    private final EmailAddress owner;
    private final String algorithm;
    private final KeyPurpose purpose;
    private final String provider;
    private final String providerRef;
    private final String certificateId;
    private KeyStatus status;
    private Instant lastUsedAt;
    private final String rotatedFromKeyId;
    private final Instant createdAt;
    private Instant updatedAt;

    public KeyRecord(String keyId,
                     EmailAddress owner,
                     String algorithm,
                     KeyPurpose purpose,
                     String provider,
                     String providerRef,
                     String certificateId,
                     KeyStatus status,
                     Instant lastUsedAt,
                     String rotatedFromKeyId,
                     Instant createdAt,
                     Instant updatedAt) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId cannot be blank");
        }
        if (owner == null) {
            throw new IllegalArgumentException("owner cannot be null");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider cannot be blank");
        }
        if (providerRef == null || providerRef.isBlank()) {
            throw new IllegalArgumentException("providerRef cannot be blank");
        }
        this.keyId = keyId;
        this.owner = owner;
        this.algorithm = algorithm == null || algorithm.isBlank() ? "UNKNOWN" : algorithm;
        this.purpose = purpose != null ? purpose : KeyPurpose.SMIME;
        this.provider = provider;
        this.providerRef = providerRef;
        this.certificateId = certificateId;
        this.status = status != null ? status : KeyStatus.ACTIVE;
        this.lastUsedAt = lastUsedAt;
        this.rotatedFromKeyId = rotatedFromKeyId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static KeyRecord active(String keyId,
                                   EmailAddress owner,
                                   String algorithm,
                                   KeyPurpose purpose,
                                   String provider,
                                   String providerRef,
                                   String certificateId) {
        Instant now = Instant.now();
        return new KeyRecord(keyId, owner, algorithm, purpose, provider, providerRef,
                certificateId, KeyStatus.ACTIVE, null, null, now, now);
    }

    public String managedRef() {
        return MANAGED_KEY_REF_PREFIX + keyId;
    }

    public void markUsed(Instant when) {
        this.lastUsedAt = when != null ? when : Instant.now();
        this.updatedAt = this.lastUsedAt;
    }

    public void disable() {
        this.status = KeyStatus.DISABLED;
        this.updatedAt = Instant.now();
    }

    public boolean active() {
        return status == KeyStatus.ACTIVE;
    }

    public String getKeyId() {
        return keyId;
    }

    public EmailAddress getOwner() {
        return owner;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public KeyPurpose getPurpose() {
        return purpose;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public KeyStatus getStatus() {
        return status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public String getRotatedFromKeyId() {
        return rotatedFromKeyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
