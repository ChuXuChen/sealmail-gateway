package com.sealmail.app.usecase.quarantine;

import com.sealmail.domain.quarantine.QuarantinedMail;

public interface QuarantineMailReleaseRelay {

    void relay(QuarantinedMail mail, boolean encryptBeforeRelay);

    default void relay(QuarantinedMail mail) {
        relay(mail, false);
    }
}
