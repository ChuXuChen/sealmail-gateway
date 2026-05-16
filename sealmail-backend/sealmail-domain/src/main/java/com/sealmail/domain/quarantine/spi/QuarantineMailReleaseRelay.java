package com.sealmail.domain.quarantine.spi;

import com.sealmail.domain.quarantine.QuarantinedMail;

public interface QuarantineMailReleaseRelay {

    void relay(QuarantinedMail mail, boolean encryptBeforeRelay);

    default void relay(QuarantinedMail mail) {
        relay(mail, false);
    }
}
