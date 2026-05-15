package com.sealmail.domain.quarantine;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.shared.exception.DomainException;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuarantinedMailTest {

    @Test
    void restorePreservesStoredStateAndTimestamps() {
        Instant quarantinedAt = Instant.parse("2026-05-14T08:00:00Z");
        Instant resolvedAt = Instant.parse("2026-05-14T09:00:00Z");
        byte[] rawContent = "From: sender@example.com\r\n\r\nbody".getBytes();

        QuarantinedMail mail = QuarantinedMail.restore(
                "q-1",
                "msg-1",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                MailDirection.INBOUND,
                "127.0.0.1",
                QuarantineReason.EMAIL_AUTH_FAILED,
                "spf failed",
                QuarantineStatus.RELEASED,
                quarantinedAt,
                resolvedAt,
                "admin",
                "ok",
                rawContent
        );

        assertEquals(QuarantineStatus.RELEASED, mail.getStatus());
        assertEquals(MailDirection.INBOUND, mail.getDirection());
        assertEquals(quarantinedAt, mail.getCreatedAt());
        assertEquals(resolvedAt, mail.getResolvedAt());
        assertEquals("admin", mail.getProcessedBy());
        assertArrayEquals(rawContent, mail.getRawContent());
        assertEquals(0, mail.getDomainEvents().size());
    }

    @Test
    void forceReleaseAllowsRejectedMail() {
        QuarantinedMail mail = QuarantinedMail.create(
                "q-1",
                "msg-1",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "DLP QUARANTINE: detail",
                "raw".getBytes()
        );
        mail.reject("admin", "reject");

        assertThrows(DomainException.class, () -> mail.release("admin", "release"));

        mail.release("admin", "release", true);

        assertEquals(QuarantineStatus.RELEASED, mail.getStatus());
        assertEquals("release", mail.getProcessComment());
    }
}
