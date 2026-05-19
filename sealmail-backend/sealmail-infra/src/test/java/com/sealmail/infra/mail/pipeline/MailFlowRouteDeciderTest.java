package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailFlowRouteDeciderTest {

    @Test
    void deliveryRouteCompletesProcessingFailedWhenDecisionRequiresQuarantine() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = MailProcessing.create(envelope("raw".getBytes()), MailDirection.OUTBOUND);
        repository.save(processing);
        MailFlowRouteDecider decider = new MailFlowRouteDecider(new MailProcessingTracker(repository));
        MailProcessingContext context = MailProcessingContext.create(envelope("raw".getBytes()))
                .withProcessingId(processing.getId())
                .withDecision(MailProcessingDecision.none().withQuarantine("POLICY_VIOLATION", "blocked"));

        MailFlowRoute route = decider.deliveryRoute(message("raw".getBytes(), context));

        assertEquals(MailFlowRoute.QUARANTINE, route);
        assertEquals(ProcessingResult.FAILED, processing.getResult());
    }

    @Test
    void releaseRoutingUsesContextDirectionAndEncryptionDecision() {
        MailFlowRouteDecider decider = new MailFlowRouteDecider(new MailProcessingTracker(new InMemoryMailProcessingRepository()));
        MailProcessingContext inbound = MailProcessingContext.create(envelope("raw".getBytes()))
                .withDirection(MailDirection.INBOUND);
        MailProcessingContext outbound = inbound.withDirection(MailDirection.OUTBOUND);
        MailProcessingContext encryptedInbound = inbound.withDecision(inbound.decision().withMustEncrypt(true));

        assertEquals(MailFlowRoute.RELEASE_INBOUND, decider.releaseDirectionRoute(message("raw".getBytes(), inbound)));
        assertEquals(MailFlowRoute.RELEASE_OUTBOUND, decider.releaseDirectionRoute(message("raw".getBytes(), outbound)));
        assertEquals(MailFlowRoute.ENCRYPT_THEN_RELAY, decider.inboundReleaseRoute(message("raw".getBytes(), encryptedInbound)));
        assertEquals(MailFlowRoute.RELAY, decider.inboundReleaseRoute(message("raw".getBytes(), inbound)));
    }

    private static Message<byte[]> message(byte[] payload, MailProcessingContext context) {
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailEnvelope envelope(byte[] payload) {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload);
    }

    private static class InMemoryMailProcessingRepository implements MailProcessingRepository {
        private MailProcessing value;

        @Override
        public MailProcessing save(MailProcessing mailProcessing) {
            this.value = mailProcessing;
            return mailProcessing;
        }

        @Override
        public Optional<MailProcessing> findById(String id) {
            return value != null && value.getId().equals(id) ? Optional.of(value) : Optional.empty();
        }

        @Override
        public List<MailProcessing> findByMessageId(String messageId) {
            return List.of();
        }

        @Override
        public List<MailProcessing> findByResult(ProcessingResult result) {
            return List.of();
        }
    }
}
