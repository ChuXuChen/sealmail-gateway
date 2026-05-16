package com.sealmail.infra.quarantine;

import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingQuarantineNotificationAdapter implements QuarantineNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingQuarantineNotificationAdapter.class);

    @Override
    public void notifyCreated(QuarantinedMail quarantinedMail) {
        log.info("Quarantine notification requested: quarantineId={}, messageId={}, sender={}, reason={}",
                quarantinedMail.getId(),
                quarantinedMail.getMessageId(),
                quarantinedMail.getSender().getValue(),
                quarantinedMail.getReason());
    }
}
