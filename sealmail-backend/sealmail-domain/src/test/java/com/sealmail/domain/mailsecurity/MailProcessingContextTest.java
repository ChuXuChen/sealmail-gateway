package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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

    @Test
    void exposesStableSubContextsWithoutLeakingMutablePayload() {
        byte[] body = "body".getBytes();
        MailProcessingContext context = MailProcessingContext.initial(
                        envelope(body),
                        MailDirection.OUTBOUND,
                "api",
                body,
                "subject",
                "127.0.0.1")
                .withRelayProfile(new RelayProfile("relay.example", 25, null, null, 1000, null));

        MailContentSnapshot content = context.contentSnapshot();
        MailSecurityContext security = context.securityContext();
        MailDeliveryContext delivery = context.deliveryContext();
        MailTraceContext trace = context.traceContext();

        assertEquals(context.envelope(), content.envelope());
        assertEquals("subject", content.subject());
        assertEquals(CryptoProfile.AUTO, security.cryptoProfile());
        assertEquals(context.decision(), security.decision());
        assertEquals(MailDirection.OUTBOUND, delivery.direction());
        assertEquals("relay.example", delivery.relayProfile().host());
        assertEquals(context.processingId(), trace.processingId());
        assertEquals(context.auditTrace(), trace.auditTrace());
        assertNotSame(context.originalMailContent(), content.originalMailContent());
    }

    private static MailEnvelope envelope() {
        return envelope("body".getBytes());
    }

    private static MailEnvelope envelope(byte[] body) {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                body);
    }
}
