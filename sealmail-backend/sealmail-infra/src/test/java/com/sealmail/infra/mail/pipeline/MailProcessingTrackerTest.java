package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailProcessingTrackerTest {

    @Test
    void tracksStepLifecycleAroundNativeMessageHandler() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = MailProcessing.create(envelope("raw".getBytes()), com.sealmail.domain.mailsecurity.MailDirection.OUTBOUND);
        repository.save(processing);
        MailProcessingTracker tracker = new MailProcessingTracker(repository);
        Message<byte[]> message = MessageBuilder.withPayload("raw".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT,
                        MailProcessingContext.create(envelope("raw".getBytes())).withProcessingId(processing.getId()))
                .build();

        Message<byte[]> result = tracker.executeStep(
                message,
                "native-step",
                MailProcessingErrorType.PIPELINE,
                input -> MessageBuilder.withPayload("done".getBytes()).copyHeaders(input.getHeaders()).build());

        assertArrayEquals("done".getBytes(), result.getPayload());
        assertEquals(1, processing.getSteps().size());
        assertEquals("native-step", processing.getSteps().getFirst().getStepName());
        assertTrue(processing.getSteps().getFirst().isCompleted());
        assertTrue(processing.getSteps().getFirst().isSuccess());
    }

    @Test
    void recordsQuarantineFailureWithoutRawDetail() {
        InMemoryMailProcessingRepository repository = new InMemoryMailProcessingRepository();
        MailProcessing processing = MailProcessing.create(envelope("raw".getBytes()), com.sealmail.domain.mailsecurity.MailDirection.OUTBOUND);
        repository.save(processing);
        MailProcessingTracker tracker = new MailProcessingTracker(repository);
        MailProcessingContext context = MailProcessingContext.create(envelope("raw".getBytes()))
                .withProcessingId(processing.getId())
                .withDecision(MailProcessingDecision.none().withQuarantine(
                        QuarantineReason.POLICY_VIOLATION.name(),
                        "customer body token=abc123"))
                .withRecordDisposition(MailRecordDisposition.DLP_QUARANTINE);
        Message<byte[]> message = MessageBuilder.withPayload("raw".getBytes())
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();

        tracker.executeStep(
                message,
                "dlp",
                MailProcessingErrorType.DLP,
                input -> input);

        String errorMessage = processing.getSteps().getFirst().getErrorMessage();
        assertTrue(errorMessage.contains("quarantineReason=POLICY_VIOLATION"));
        assertTrue(errorMessage.contains("detailPresent=true"));
        assertFalse(errorMessage.contains("customer body"));
        assertFalse(errorMessage.contains("abc123"));
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
