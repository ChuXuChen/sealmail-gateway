package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuarantineReleaseGuardTest {

    @Test
    void passesThroughReleaseMessageWhenNoQuarantineDecisionExists() {
        QuarantineReleaseGuard guard = new QuarantineReleaseGuard(
                new MailProcessingTracker(new InMemoryMailProcessingRepository(), null));
        Message<byte[]> message = message("raw".getBytes(), MailProcessingContext.create(envelope("raw".getBytes())));

        Message<byte[]> result = guard.failIfQuarantined(message, MailProcessingErrorType.ROUTING);

        assertArrayEquals("raw".getBytes(), result.getPayload());
    }

    @Test
    void failsReleaseAndCompletesProcessingFailedWhenRoutingRequarantinesMail() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = MailProcessing.create(envelope("raw".getBytes()), MailDirection.OUTBOUND);
        repository.save(processing);
        QuarantineReleaseGuard guard = new QuarantineReleaseGuard(new MailProcessingTracker(repository, null));
        MailProcessingContext context = MailProcessingContext.create(envelope("raw".getBytes()))
                .withProcessingId(processing.getId())
                .withDecision(MailProcessingDecision.none().withQuarantine("DOMAIN_NOT_CONFIGURED", "domain disabled"));

        MailProcessingException exception = assertThrows(MailProcessingException.class,
                () -> guard.failIfQuarantined(message("raw".getBytes(), context), MailProcessingErrorType.ROUTING));

        assertEquals(MailProcessingErrorType.ROUTING, exception.errorType());
        assertEquals("Quarantine release stopped: domain disabled", exception.getMessage());
        assertEquals(ProcessingResult.FAILED, processing.getResult());
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

        @Override
        public List<MailProcessing> findRecent(int page, int size) {
            return value != null ? List.of(value) : List.of();
        }

        @Override
        public long count() {
            return value != null ? 1 : 0;
        }
    }
}
