package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import com.sealmail.infra.mail.pipeline.PipelineStepTracker;
import com.sealmail.infra.mail.pipeline.RoutingService;
import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.QuarantineStep;
import com.sealmail.infra.mail.pipeline.step.RelayStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarantineMailReleaseRelayImplTest {

    @Test
    void outboundReleaseResumesNormalPostDlpPipeline() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.OUTBOUND);
        when(routingService.routeOutbound(any())).thenReturn(routedMessage(mail, "raw".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(signStep)))
                .thenReturn(PipelineResult.success("signed".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(encryptStep)))
                .thenReturn(PipelineResult.success("encrypted".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(dkimSignStep)))
                .thenReturn(PipelineResult.success("dkim".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(relayStep)))
                .thenReturn(PipelineResult.success("dkim".getBytes()));

        releaseRelay.relay(mail, false);

        verify(routingService).routeOutbound(any());
        verify(stepTracker).executeWithTracking(any(), eq(signStep));
        verify(stepTracker).executeWithTracking(any(), eq(encryptStep));
        verify(stepTracker).executeWithTracking(any(), eq(dkimSignStep));
        ArgumentCaptor<Message<byte[]>> relayMessage = messageCaptor();
        verify(stepTracker).executeWithTracking(relayMessage.capture(), eq(relayStep));
        assertArrayEquals("dkim".getBytes(), relayMessage.getValue().getPayload());
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.SUCCESS);
    }

    @Test
    void encryptedReleaseForcesEncryptionOnRoutedMessage() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.OUTBOUND);
        when(routingService.routeOutbound(any())).thenReturn(routedMessage(mail, "raw".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(signStep)))
                .thenReturn(PipelineResult.success("signed".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(encryptStep)))
                .thenReturn(PipelineResult.success("encrypted".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(dkimSignStep)))
                .thenReturn(PipelineResult.success("dkim".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(relayStep)))
                .thenReturn(PipelineResult.success("dkim".getBytes()));

        releaseRelay.relay(mail, true);

        ArgumentCaptor<Message<byte[]>> encryptMessage = messageCaptor();
        verify(stepTracker).executeWithTracking(encryptMessage.capture(), eq(encryptStep));
        assertEquals(Boolean.TRUE, encryptMessage.getValue().getHeaders().get("encryptionEnabled"));
        assertEquals("true", encryptMessage.getValue().getHeaders().get("mustEncrypt"));
    }

    @Test
    void inboundReleaseSkipsOutboundCryptoAndRelays() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.INBOUND);
        when(routingService.routeInbound(any())).thenReturn(routedMessage(mail, "raw".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(relayStep)))
                .thenReturn(PipelineResult.success("raw".getBytes()));

        releaseRelay.relay(mail, false);

        verify(routingService).routeInbound(any());
        verify(stepTracker, never()).executeWithTracking(any(), eq(signStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(encryptStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(dkimSignStep));
        verify(stepTracker).executeWithTracking(any(), eq(relayStep));
    }

    @Test
    void encryptedInboundReleaseEncryptsBeforeRelay() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.INBOUND);
        when(routingService.routeInbound(any())).thenReturn(routedMessage(mail, "raw".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(encryptStep)))
                .thenReturn(PipelineResult.success("encrypted".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(relayStep)))
                .thenReturn(PipelineResult.success("encrypted".getBytes()));

        releaseRelay.relay(mail, true);

        ArgumentCaptor<Message<byte[]>> encryptMessage = messageCaptor();
        verify(stepTracker).executeWithTracking(encryptMessage.capture(), eq(encryptStep));
        assertEquals(Boolean.TRUE, encryptMessage.getValue().getHeaders().get("encryptionEnabled"));
        assertEquals("true", encryptMessage.getValue().getHeaders().get("mustEncrypt"));

        ArgumentCaptor<Message<byte[]>> relayMessage = messageCaptor();
        verify(stepTracker).executeWithTracking(relayMessage.capture(), eq(relayStep));
        assertArrayEquals("encrypted".getBytes(), relayMessage.getValue().getPayload());
    }

    @Test
    void releaseFailsWhenRoutingWouldQuarantineAgain() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.OUTBOUND);
        Message<byte[]> routed = MessageBuilder.withPayload("raw".getBytes())
                .copyHeaders(routedMessage(mail, "raw".getBytes()).getHeaders())
                .setHeader("quarantineRequired", true)
                .setHeader("quarantineDetail", "domain disabled")
                .build();
        when(routingService.routeOutbound(any())).thenReturn(routed);

        assertThrows(QuarantineReleaseRelayException.class, () -> releaseRelay.relay(mail, false));
        verify(stepTracker).executeWithTracking(any(), eq(quarantineStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(signStep));
    }

    @Test
    void releaseFailsWhenAnyPipelineStepFails() {
        RoutingService routingService = mock(RoutingService.class);
        PipelineStepTracker stepTracker = mock(PipelineStepTracker.class);
        SignStep signStep = mock(SignStep.class);
        EncryptStep encryptStep = mock(EncryptStep.class);
        DkimSignStep dkimSignStep = mock(DkimSignStep.class);
        RelayStep relayStep = mock(RelayStep.class);
        QuarantineStep quarantineStep = mock(QuarantineStep.class);
        QuarantineMailReleaseRelayImpl releaseRelay =
                new QuarantineMailReleaseRelayImpl(
                        routingService,
                        stepTracker,
                        signStep,
                        encryptStep,
                        dkimSignStep,
                        relayStep,
                        quarantineStep);

        QuarantinedMail mail = mail(MailDirection.OUTBOUND);
        when(routingService.routeOutbound(any())).thenReturn(routedMessage(mail, "raw".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(signStep)))
                .thenReturn(PipelineResult.success("signed".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(encryptStep)))
                .thenReturn(PipelineResult.failure("encrypt failed"));

        assertThrows(QuarantineReleaseRelayException.class, () -> releaseRelay.relay(mail, true));
        verify(stepTracker, never()).executeWithTracking(any(), eq(relayStep));
        verify(stepTracker).executeWithTracking(any(), eq(quarantineStep));
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

    private static Message<byte[]> routedMessage(QuarantinedMail mail, byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new com.sealmail.domain.mailsecurity.MailEnvelope(
                        mail.getMessageId(),
                        mail.getSender(),
                        mail.getRecipients(),
                        mail.getRemoteAddress(),
                        "release",
                        mail.getCreatedAt(),
                        payload
                ))
                .setHeader("processingId", "processing-1")
                .setHeader("relayHost", "127.0.0.1")
                .setHeader("relayPort", mail.getDirection() == MailDirection.OUTBOUND ? 10027 : 10026)
                .setHeader("relayUseTls", false)
                .setHeader("relayUsername", "")
                .setHeader("relayPassword", "")
                .setHeader("relayTimeout", 10000)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
