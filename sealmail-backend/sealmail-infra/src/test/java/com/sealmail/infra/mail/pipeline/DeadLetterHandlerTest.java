package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterHandlerTest {

    @Test
    void handleErrorRecordsRecentEntryAndRoutesOriginalPayloadToQuarantine() {
        MessageChannel quarantineChannel = mock(MessageChannel.class);
        when(quarantineChannel.send(any())).thenReturn(true);
        MailProcessingRepository repository = mock(MailProcessingRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MailErrorDecisionHandler decisionHandler = new MailErrorDecisionHandler(repository, publisher);
        DeadLetterHandler handler = new DeadLetterHandler(quarantineChannel, decisionHandler);

        byte[] originalPayload = "raw mail".getBytes();
        MailProcessingContext context = MailProcessingContext.create(new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        originalPayload))
                .withProcessingId("processing-1");
        Throwable error = new MailProcessingException(
                MailProcessingErrorType.RELAY,
                "relay down",
                context);
        Message<byte[]> failedMessage = MessageBuilder.withPayload(originalPayload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
        Message<Throwable> errorMessage = new ErrorMessage(error, failedMessage);

        handler.handleError(errorMessage);

        assertEquals(1, handler.getTotalDeadLetters());
        assertEquals(1, handler.getRecentErrors().size());
        DeadLetterHandler.DeadLetterEntry entry = handler.getRecentErrors().getFirst();
        assertEquals("relay down", entry.getErrorMessage());
        assertTrue(entry.getStackTrace().contains("relay down"));

        ArgumentCaptor<Message<byte[]>> quarantineMessage = messageCaptor();
        verify(quarantineChannel).send(quarantineMessage.capture());
        assertArrayEquals(originalPayload, quarantineMessage.getValue().getPayload());
        MailProcessingContext quarantineContext = (MailProcessingContext) quarantineMessage.getValue()
                .getHeaders()
                .get(MailProcessingHeaders.CONTEXT);
        assertEquals("POLICY_VIOLATION", quarantineContext.decision().quarantine().reason());
        assertTrue(quarantineContext.decision().quarantine().detail().contains("processingId=processing-1"));
        assertTrue(quarantineContext.decision().quarantine().detail().contains("errorType=RELAY"));
        assertTrue(quarantineContext.decision().quarantine().detail().contains("relay down"));
        assertEquals(MailRecordDisposition.EXCEPTION, quarantineContext.recordDisposition());
        assertEquals("processing-1", quarantineContext.processingId());
        verify(publisher).publishEvent(any());
    }

    @Test
    void decisionHandlerUpdatesProcessingWhenErrorHasProcessingId() {
        MailProcessingRepository repository = mock(MailProcessingRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MailErrorDecisionHandler decisionHandler = new MailErrorDecisionHandler(repository, publisher);
        byte[] payload = "raw mail".getBytes();
        MailEnvelope envelope = new MailEnvelope(
                "msg-2@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload);
        MailProcessing processing = MailProcessing.create(envelope, MailDirection.OUTBOUND);
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withProcessingId(processing.getId());
        when(repository.findById(processing.getId())).thenReturn(Optional.of(processing));

        decisionHandler.decide(new ErrorMessage(
                new MailProcessingException(MailProcessingErrorType.DLP, "scanner down", context),
                MessageBuilder.withPayload(payload)
                        .setHeader(MailProcessingHeaders.CONTEXT, context)
                        .build()));

        assertEquals(ProcessingResult.FAILED, processing.getResult());
        verify(repository).save(processing);
    }

    @Test
    void quarantinePersistenceFailureStaysDeadLetterInsteadOfReenteringQuarantine() {
        MessageChannel quarantineChannel = mock(MessageChannel.class);
        MailProcessingRepository repository = mock(MailProcessingRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MailErrorDecisionHandler decisionHandler = new MailErrorDecisionHandler(repository, publisher);
        DeadLetterHandler handler = new DeadLetterHandler(quarantineChannel, decisionHandler);
        byte[] payload = "raw mail".getBytes();
        MailEnvelope envelope = new MailEnvelope(
                "msg-3@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload);
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withProcessingId("processing-3");

        handler.handleError(new ErrorMessage(
                new MailProcessingException(MailProcessingErrorType.QUARANTINE, "db down", context),
                MessageBuilder.withPayload(payload)
                        .setHeader(MailProcessingHeaders.CONTEXT, context)
                        .build()));

        assertEquals(1, handler.getTotalDeadLetters());
        verify(quarantineChannel, never()).send(any());
        verify(publisher).publishEvent(any());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
