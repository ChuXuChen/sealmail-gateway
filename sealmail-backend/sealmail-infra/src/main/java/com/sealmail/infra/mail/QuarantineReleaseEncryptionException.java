package com.sealmail.infra.mail;

import com.sealmail.app.exception.BusinessException;

public class QuarantineReleaseEncryptionException extends BusinessException {

    public QuarantineReleaseEncryptionException(String id, String message) {
        super("QUARANTINE_RELEASE_ENCRYPTION_FAILED",
                "Failed to encrypt quarantined mail before release: " + id + ". " + message);
    }

    public QuarantineReleaseEncryptionException(String id, Throwable cause) {
        super("QUARANTINE_RELEASE_ENCRYPTION_FAILED",
                "Failed to encrypt quarantined mail before release: " + id, cause);
    }
}
