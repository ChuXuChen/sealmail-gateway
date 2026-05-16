package com.sealmail.infra.mail.pipeline;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

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
        Message<Throwable> errorMessage = MessageBuilder.withPayload(error)
                .setHeader("originalMessage", originalPayload)
                .setHeader("processingId", "processing-1")
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
        assertEquals("PIPELINE_ERROR", quarantineMessage.getValue().getHeaders().get("quarantineReason"));
        assertEquals("relay down", quarantineMessage.getValue().getHeaders().get("quarantineDetail"));
        assertEquals(MailRecordDisposition.EXCEPTION.name(),
                quarantineMessage.getValue().getHeaders().get("mailRecordDisposition"));
        assertEquals("processing-1", quarantineMessage.getValue().getHeaders().get("processingId"));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
