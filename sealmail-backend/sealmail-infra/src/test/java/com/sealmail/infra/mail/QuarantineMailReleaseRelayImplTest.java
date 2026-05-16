package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantineMailReleaseRelayImplTest {

    @Test
    void releaseEnqueuesMailToSpringIntegrationReleaseChannel() {
        MessageChannel releaseChannel = mock(MessageChannel.class);
        when(releaseChannel.send(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        QuarantineMailReleaseRelayImpl releaseRelay = new QuarantineMailReleaseRelayImpl(releaseChannel);
        QuarantinedMail mail = mail(MailDirection.OUTBOUND);

        releaseRelay.relay(mail, false);

        ArgumentCaptor<Message<byte[]>> captor = messageCaptor();
        verify(releaseChannel).send(captor.capture());
        Message<byte[]> message = captor.getValue();
        assertArrayEquals(mail.getRawContent(), message.getPayload());
        MailProcessingContext context = context(message);
        assertEquals(mail.getId(), context.quarantineReleaseId());
        assertEquals(MailDirection.OUTBOUND, context.direction());
        assertEquals("dlp_quarantine_release", context.auditTrace().submissionType());
    }

    @Test
    void encryptedReleaseSetsEncryptionRequirementBeforeEnteringReleaseFlow() {
        MessageChannel releaseChannel = mock(MessageChannel.class);
        when(releaseChannel.send(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        QuarantineMailReleaseRelayImpl releaseRelay = new QuarantineMailReleaseRelayImpl(releaseChannel);
        QuarantinedMail mail = mail(MailDirection.INBOUND);

        releaseRelay.relay(mail, true);

        ArgumentCaptor<Message<byte[]>> captor = messageCaptor();
        verify(releaseChannel).send(captor.capture());
        MailProcessingContext context = context(captor.getValue());
        assertEquals(MailDirection.INBOUND, context.direction());
        assertTrue(context.decision().encryptionRequired());
        assertTrue(context.decision().mustEncrypt());
    }

    @Test
    void releaseFailsWhenReleaseChannelRejectsMessage() {
        MessageChannel releaseChannel = mock(MessageChannel.class);
        when(releaseChannel.send(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        QuarantineMailReleaseRelayImpl releaseRelay = new QuarantineMailReleaseRelayImpl(releaseChannel);

        assertThrows(QuarantineReleaseRelayException.class,
                () -> releaseRelay.relay(mail(MailDirection.OUTBOUND), false));
    }

    @Test
    void releaseAdapterDoesNotOwnPipelineSteps() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sealmail/infra/mail/QuarantineMailReleaseRelayImpl.java"));

        assertFalse(source.contains("RoutingService"));
        assertFalse(source.contains("PipelineStepTracker"));
        assertFalse(source.contains("MailPipelineStep"));
        assertFalse(source.contains("SignStep"));
        assertFalse(source.contains("EncryptStep"));
        assertFalse(source.contains("DkimSignStep"));
        assertFalse(source.contains("RelayStep"));
        assertFalse(source.contains("QuarantineStep"));
        assertFalse(source.contains("executeMessageWithTracking"));
    }

    private static QuarantinedMail mail(MailDirection direction) {
        return QuarantinedMail.create(
                "q-1",
                "msg-1@example.com",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                direction,
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "detail",
                "raw".getBytes()
        );
    }

    private static MailProcessingContext context(Message<byte[]> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
