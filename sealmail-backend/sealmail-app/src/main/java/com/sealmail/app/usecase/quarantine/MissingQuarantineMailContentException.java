package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.exception.BusinessException;

public class MissingQuarantineMailContentException extends BusinessException {

    public MissingQuarantineMailContentException(String id) {
        super("QUARANTINE_CONTENT_MISSING",
                "Quarantined mail cannot be released because original content is missing: " + id);
    }
}
