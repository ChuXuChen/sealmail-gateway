package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterHandlerTest {

    @Test
    void handleErrorRecordsRecentEntryAndRoutesOriginalPayloadToQuarantine() {
        MessageChannel quarantineChannel = mock(MessageChannel.class);
        when(quarantineChannel.send(any())).thenReturn(true);
        DeadLetterHandler handler = new DeadLetterHandler(quarantineChannel);

        byte[] originalPayload = "raw mail".getBytes();
        Throwable error = new RuntimeException("relay down");
        MailProcessingContext context = MailProcessingContext.create(new MailEnvelope(
                        "msg-1@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        originalPayload))
                .withProcessingId("processing-1");
        Message<Throwable> errorMessage = MessageBuilder.withPayload(error)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();

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
        assertEquals("PIPELINE_ERROR", quarantineContext.decision().quarantine().reason());
        assertEquals("relay down", quarantineContext.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.EXCEPTION, quarantineContext.recordDisposition());
        assertEquals("processing-1", quarantineContext.processingId());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
