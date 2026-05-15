package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelineStepTrackerTest {

    @Test
    void publishesEventsReturnedByStepResult() {
        MailProcessingRepository repository = new EmptyMailProcessingRepository();
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        PipelineStepTracker tracker = new PipelineStepTracker(repository, publisher);
        MailSigned event = new MailSigned(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                new CertificateId("ABC123"));

        PipelineResult result = tracker.executeWithTracking(
                MessageBuilder.withPayload("body".getBytes()).build(),
                new EventProducingStep(event));

        assertEquals(true, result.success());
        verify(publisher).publishEvent(event);
    }

    private static class EventProducingStep implements MailPipelineStep {
        private final DomainEvent event;

        EventProducingStep(DomainEvent event) {
            this.event = event;
        }

        @Override
        public PipelineResult execute(Message<byte[]> message) {
            return PipelineResult.success(message.getPayload(), event);
        }

        @Override
        public String getStepName() {
            return "event-producing";
        }
    }

    private static class EmptyMailProcessingRepository implements MailProcessingRepository {

        @Override
        public MailProcessing save(MailProcessing mailProcessing) {
            return mailProcessing;
        }

        @Override
        public Optional<MailProcessing> findById(String id) {
            return Optional.empty();
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
