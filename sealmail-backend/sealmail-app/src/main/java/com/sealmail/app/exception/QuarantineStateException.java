package com.sealmail.app.exception;

public class QuarantineStateException extends BusinessException {

    public QuarantineStateException(String code, String message) {
        super(code, message);
    }

    public static QuarantineStateException alreadyReleased(String id) {
        return new QuarantineStateException("QUARANTINE_ALREADY_RELEASED",
                "Quarantined mail has already been released: " + id);
    }

    public static QuarantineStateException alreadyRejected(String id) {
        return new QuarantineStateException("QUARANTINE_ALREADY_REJECTED",
                "Quarantined mail has already been rejected: " + id);
    }

    public static QuarantineStateException invalidStateForOperation(String id, String operation) {
        return new QuarantineStateException("QUARANTINE_INVALID_STATE",
                String.format("Cannot perform %s on quarantined mail: %s", operation, id));
    }
}
