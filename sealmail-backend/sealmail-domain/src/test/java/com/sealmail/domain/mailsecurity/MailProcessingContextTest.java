package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MailProcessingContextTest {

    @Test
    void createAssignsStableProcessingIdAtEntry() {
        MailProcessingContext context = MailProcessingContext.create(envelope());

        assertNotNull(context.processingId());
    }

    @Test
    void initialPropagatesProcessingIdIntoAuditTrace() {
        MailProcessingContext context = MailProcessingContext.initial(
                envelope(),
                MailDirection.OUTBOUND,
                "api",
                "body".getBytes(),
                "subject",
                "127.0.0.1");

        assertNotNull(context.processingId());
        assertNotNull(context.auditTrace());
        assertEquals(context.processingId(), context.auditTrace().processingId());
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes());
    }
}
