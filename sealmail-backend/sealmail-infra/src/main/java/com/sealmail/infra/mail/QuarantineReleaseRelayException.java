package com.sealmail.infra.mail;

import com.sealmail.domain.shared.exception.CodedDomainException;

public class QuarantineReleaseRelayException extends CodedDomainException {

    public QuarantineReleaseRelayException(String id, String message) {
        super("QUARANTINE_RELEASE_RELAY_FAILED",
                "Failed to relay quarantined mail: " + id + ". " + message);
    }

    public QuarantineReleaseRelayException(String id, Throwable cause) {
        super("QUARANTINE_RELEASE_RELAY_FAILED",
                "Failed to relay quarantined mail: " + id + ". " + cause.getMessage(), cause);
    }
}
