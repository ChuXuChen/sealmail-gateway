package com.sealmail.infra.mail;

import com.sealmail.app.exception.BusinessException;

public class QuarantineReleaseRelayException extends BusinessException {

    public QuarantineReleaseRelayException(String id, Throwable cause) {
        super("QUARANTINE_RELEASE_RELAY_FAILED",
                "Failed to relay quarantined mail: " + id, cause);
    }
}
