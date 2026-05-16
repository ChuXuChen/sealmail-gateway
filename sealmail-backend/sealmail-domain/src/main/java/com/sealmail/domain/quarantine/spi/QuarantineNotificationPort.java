package com.sealmail.domain.quarantine.spi;

import com.sealmail.domain.quarantine.QuarantinedMail;

public interface QuarantineNotificationPort {

    void notifyCreated(QuarantinedMail quarantinedMail);
}
