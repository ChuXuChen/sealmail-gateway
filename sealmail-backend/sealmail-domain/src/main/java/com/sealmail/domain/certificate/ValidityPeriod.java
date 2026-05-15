package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.model.ValueObject;

import java.time.Instant;
import java.util.Objects;

public final class ValidityPeriod extends ValueObject {

    private final Instant notBefore;
    private final Instant notAfter;

    public ValidityPeriod(Instant notBefore, Instant notAfter) {
        if (notBefore == null) {
            throw new IllegalArgumentException("notBefore cannot be null");
        }
        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter cannot be null");
        }
        if (!notAfter.isAfter(notBefore)) {
            throw new IllegalArgumentException("notAfter must be after notBefore");
        }
        this.notBefore = notBefore;
        this.notAfter = notAfter;
    }

    public boolean isValidAt(Instant instant) {
        return !instant.isBefore(notBefore) && !instant.isAfter(notAfter);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(notAfter);
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidityPeriod that = (ValidityPeriod) o;
        return notBefore.equals(that.notBefore) && notAfter.equals(that.notAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notBefore, notAfter);
    }
}
