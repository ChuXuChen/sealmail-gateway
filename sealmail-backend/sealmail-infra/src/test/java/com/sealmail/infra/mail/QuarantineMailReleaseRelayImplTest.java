package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
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
        whenTracked(stepTracker, signStep, PipelineResult.success("signed".getBytes()));
        whenTracked(stepTracker, encryptStep, PipelineResult.success("encrypted".getBytes()));
        whenTracked(stepTracker, dkimSignStep, PipelineResult.success("dkim".getBytes()));
        whenTracked(stepTracker, relayStep, PipelineResult.success("dkim".getBytes()));

        releaseRelay.relay(mail, false);

        verify(routingService).routeOutbound(any());
        verify(stepTracker).executeMessageWithTracking(any(), eq(signStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(encryptStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(dkimSignStep));
        ArgumentCaptor<Message<byte[]>> relayMessage = messageCaptor();
        verify(stepTracker).executeMessageWithTracking(relayMessage.capture(), eq(relayStep));
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
        whenTracked(stepTracker, signStep, PipelineResult.success("signed".getBytes()));
        whenTracked(stepTracker, encryptStep, PipelineResult.success("encrypted".getBytes()));
        whenTracked(stepTracker, dkimSignStep, PipelineResult.success("dkim".getBytes()));
        whenTracked(stepTracker, relayStep, PipelineResult.success("dkim".getBytes()));

        releaseRelay.relay(mail, true);

        ArgumentCaptor<Message<byte[]>> encryptMessage = messageCaptor();
        verify(stepTracker).executeMessageWithTracking(encryptMessage.capture(), eq(encryptStep));
        MailProcessingContext context = context(encryptMessage.getValue());
        assertEquals(true, context.decision().encryptionRequired());
        assertEquals(true, context.decision().mustEncrypt());
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
        whenTracked(stepTracker, relayStep, PipelineResult.success("raw".getBytes()));

        releaseRelay.relay(mail, false);

        verify(routingService).routeInbound(any());
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(signStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(encryptStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(dkimSignStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(relayStep));
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
        whenTracked(stepTracker, encryptStep, PipelineResult.success("encrypted".getBytes()));
        whenTracked(stepTracker, relayStep, PipelineResult.success("encrypted".getBytes()));

        releaseRelay.relay(mail, true);

        ArgumentCaptor<Message<byte[]>> encryptMessage = messageCaptor();
        verify(stepTracker).executeMessageWithTracking(encryptMessage.capture(), eq(encryptStep));
        MailProcessingContext context = context(encryptMessage.getValue());
        assertEquals(true, context.decision().encryptionRequired());
        assertEquals(true, context.decision().mustEncrypt());

        ArgumentCaptor<Message<byte[]>> relayMessage = messageCaptor();
        verify(stepTracker).executeMessageWithTracking(relayMessage.capture(), eq(relayStep));
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
                .setHeader(MailProcessingHeaders.CONTEXT, context(routedMessage(mail, "raw".getBytes()))
                        .withDecision(MailProcessingDecision.none()
                                .withQuarantine("DOMAIN_NOT_CONFIGURED", "domain disabled"))
                        .withRecordDisposition(MailRecordDisposition.EXCEPTION))
                .build();
        when(routingService.routeOutbound(any())).thenReturn(routed);

        assertThrows(QuarantineReleaseRelayException.class, () -> releaseRelay.relay(mail, false));
        verify(stepTracker).executeMessageWithTracking(any(), eq(quarantineStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(signStep));
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
        whenTracked(stepTracker, signStep, PipelineResult.success("signed".getBytes()));
        whenTracked(stepTracker, encryptStep, PipelineResult.failure("encrypt failed"));

        assertThrows(QuarantineReleaseRelayException.class, () -> releaseRelay.relay(mail, true));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(relayStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(quarantineStep));
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
        MailEnvelope envelope = new MailEnvelope(
                mail.getMessageId(),
                mail.getSender(),
                mail.getRecipients(),
                mail.getRemoteAddress(),
                "release",
                mail.getCreatedAt(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.create(envelope)
                .withDirection(mail.getDirection())
                .withProcessingId("processing-1")
                .withRelayProfile(new RelayProfile(
                        "127.0.0.1",
                        mail.getDirection() == MailDirection.OUTBOUND ? 10027 : 10026,
                        false,
                        "",
                        "",
                        10000,
                        null));
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailProcessingContext context(Message<byte[]> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }

    private static void whenTracked(PipelineStepTracker stepTracker,
                                    MailPipelineStep step,
                                    PipelineResult result) {
        when(stepTracker.executeMessageWithTracking(any(), eq(step)))
                .thenAnswer(invocation -> trackedMessage(invocation.getArgument(0), result));
    }

    private static Message<byte[]> trackedMessage(Message<byte[]> original, PipelineResult result) {
        byte[] payload = result.payload() != null && result.payload().length > 0
                ? result.payload()
                : original.getPayload();
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders());
        if (result.headers() != null) {
            result.headers().forEach((name, value) -> {
                if (name != null && value != null) {
                    builder.setHeader(name, value);
                }
            });
        }
        if (!result.success()) {
            MailProcessingContext context = context(original);
            builder.setHeader(MailProcessingHeaders.CONTEXT, context
                    .withDecision(context.decision().withQuarantine(
                            result.quarantineReason() != null ? result.quarantineReason() : "POLICY_VIOLATION",
                            result.quarantineDetail() != null ? result.quarantineDetail() : result.errorMessage()))
                    .withRecordDisposition(result.recordDisposition() != null
                            ? result.recordDisposition()
                            : MailRecordDisposition.EXCEPTION));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Message<byte[]>> messageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }
}
