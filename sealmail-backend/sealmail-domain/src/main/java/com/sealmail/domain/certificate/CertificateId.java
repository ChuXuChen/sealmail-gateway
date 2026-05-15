package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.model.ValueObject;

import java.util.Objects;

public final class CertificateId extends ValueObject {

    private final String thumbprint;

    public CertificateId(String thumbprint) {
        if (thumbprint == null || thumbprint.isBlank()) {
            throw new IllegalArgumentException("Certificate thumbprint cannot be null or blank");
        }
        this.thumbprint = thumbprint.toLowerCase();
    }

    public String getThumbprint() {
        return thumbprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateId that = (CertificateId) o;
        return thumbprint.equals(that.thumbprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thumbprint);
    }

    @Override
    public String toString() {
        return thumbprint;
    }
}
