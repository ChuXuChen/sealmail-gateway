package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.mail.pipeline.step.DecryptStep;
import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.DlpStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.MailAuthenticationStep;
import com.sealmail.infra.mail.pipeline.step.QuarantineStep;
import com.sealmail.infra.mail.pipeline.step.RelayStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import com.sealmail.infra.mail.pipeline.step.VerifyStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = MailPipelineFlowCharacterizationTest.Config.class)
class MailPipelineFlowCharacterizationTest {

    @Autowired
    MessageChannel mailOutboundChannel;
    @Autowired
    MessageChannel mailInboundChannel;
    @Autowired
    MessageChannel quarantineReleaseChannel;
    @Autowired
    QueueChannel relayChannel;
    @Autowired
    QueueChannel quarantineChannel;
    @Autowired
    QueueChannel errorChannel;
    @Autowired
    RoutingService routingService;
    @Autowired
    MailProcessingTracker tracker;
    @Autowired
    DlpStep dlpStep;
    @Autowired
    SignStep signStep;
    @Autowired
    EncryptStep encryptStep;
    @Autowired
    DkimSignStep dkimSignStep;
    @Autowired
    RelayStep relayStep;
    @Autowired
    QuarantineStep quarantineStep;
    @Autowired
    MailAuthenticationStep mailAuthenticationStep;
    @Autowired
    DecryptStep decryptStep;
    @Autowired
    VerifyStep verifyStep;

    @BeforeEach
    void resetMocksAndChannels() {
        reset(routingService, tracker, dlpStep, signStep, encryptStep, dkimSignStep,
                mailAuthenticationStep, decryptStep, verifyStep, relayStep, quarantineStep);
        drain(relayChannel);
        drain(quarantineChannel);
        drain(errorChannel);
    }

    @Test
    void outboundHappyPathRoutesThroughDlpSignEncryptDkimThenRelayChannel() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("dlp", MailProcessingErrorType.DLP, "after-dlp".getBytes());
        whenTracked("sign", MailProcessingErrorType.SIGNING, "signed".getBytes());
        whenTracked("encrypt", MailProcessingErrorType.ENCRYPTION, "encrypted".getBytes());
        whenTracked("dkim-sign", MailProcessingErrorType.DKIM_SIGNING, "dkim".getBytes());

        assertTrue(mailOutboundChannel.send(outboundMessage(raw)));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("dkim".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeOutbound(any());
        verifyTracked("dlp", MailProcessingErrorType.DLP);
        verifyTracked("sign", MailProcessingErrorType.SIGNING);
        verifyTracked("encrypt", MailProcessingErrorType.ENCRYPTION);
        verifyTracked("dkim-sign", MailProcessingErrorType.DKIM_SIGNING);
        verify(tracker, never()).completeProcessing("processing-1", ProcessingResult.FAILED);
    }

    @Test
    void outboundDlpQuarantineCompletesFailedAndRoutesContextMessageToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTrackedQuarantine("dlp", MailProcessingErrorType.DLP,
                "POLICY_VIOLATION", "DLP QUARANTINE", MailRecordDisposition.DLP_QUARANTINE);

        assertTrue(mailOutboundChannel.send(outboundMessage(raw)));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("POLICY_VIOLATION", context.decision().quarantine().reason());
        assertEquals("DLP QUARANTINE", context.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.DLP_QUARANTINE, context.recordDisposition());
        assertNull(relayChannel.receive(0));
        verify(tracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verifyNotTracked("sign");
        verifyNotTracked("encrypt");
        verifyNotTracked("dkim-sign");
    }

    @Test
    void outboundRoutingQuarantineContextShortCircuitsStepsAndRoutesRawPayloadToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withQuarantine(invocation.getArgument(0),
                        "processing-1",
                        "DOMAIN_NOT_CONFIGURED",
                        "domain disabled",
                        MailRecordDisposition.EXCEPTION));

        assertTrue(mailOutboundChannel.send(outboundMessage(raw)));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("DOMAIN_NOT_CONFIGURED", context.decision().quarantine().reason());
        assertEquals("domain disabled", context.decision().quarantine().detail());
        assertNull(relayChannel.receive(0));
        verify(tracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verifyNotTracked("dlp");
        verifyNotTracked("sign");
    }

    @Test
    void inboundHappyPathRoutesThroughAuthDecryptVerifyDlpThenRelayChannel() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("mail-auth", MailProcessingErrorType.AUTHENTICATION, "authenticated".getBytes());
        whenTracked("decrypt", MailProcessingErrorType.DECRYPTION, "decrypted".getBytes());
        whenTracked("verify-signature", MailProcessingErrorType.VERIFICATION, "verified".getBytes());
        whenTracked("dlp", MailProcessingErrorType.DLP, "after-dlp".getBytes());

        assertTrue(mailInboundChannel.send(inboundMessage(raw)));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("after-dlp".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeInbound(any());
        verifyTracked("mail-auth", MailProcessingErrorType.AUTHENTICATION);
        verifyTracked("decrypt", MailProcessingErrorType.DECRYPTION);
        verifyTracked("verify-signature", MailProcessingErrorType.VERIFICATION);
        verifyTracked("dlp", MailProcessingErrorType.DLP);
    }

    @Test
    void inboundAuthenticationQuarantineCompletesFailedAndRoutesToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTrackedQuarantine("mail-auth", MailProcessingErrorType.AUTHENTICATION,
                "EMAIL_AUTH_FAILED", "spf=FAIL", MailRecordDisposition.EXCEPTION);

        assertTrue(mailInboundChannel.send(inboundMessage(raw)));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("EMAIL_AUTH_FAILED", context.decision().quarantine().reason());
        assertEquals("spf=FAIL", context.decision().quarantine().detail());
        assertNull(relayChannel.receive(0));
        verify(tracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verifyNotTracked("decrypt");
        verifyNotTracked("verify-signature");
        verifyNotTracked("dlp");
    }

    @Test
    void inboundDecryptQuarantineShortCircuitsVerifyAndDlp() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("mail-auth", MailProcessingErrorType.AUTHENTICATION, "authenticated".getBytes());
        whenTrackedQuarantine("decrypt", MailProcessingErrorType.DECRYPTION,
                "DECRYPT_FAILED", "recipient key unavailable", MailRecordDisposition.EXCEPTION);

        assertTrue(mailInboundChannel.send(inboundMessage(raw)));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals("authenticated".getBytes(), (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("DECRYPT_FAILED", context.decision().quarantine().reason());
        assertEquals("recipient key unavailable", context.decision().quarantine().detail());
        assertNull(relayChannel.receive(0));
        verify(tracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verifyNotTracked("verify-signature");
        verifyNotTracked("dlp");
    }

    @Test
    void outboundStepExceptionRoutesToErrorChannelAndStopsRelay() {
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        when(tracker.executeStep(any(), eq("dlp"), eq(MailProcessingErrorType.DLP), any()))
                .thenThrow(new IllegalStateException("scanner down"));

        assertTrue(mailOutboundChannel.send(outboundMessage("raw".getBytes())));

        Message<?> error = errorChannel.receive(1000);
        assertEquals("scanner down", ((Throwable) error.getPayload()).getCause().getMessage());
        assertNull(relayChannel.receive(0));
        assertNull(quarantineChannel.receive(0));
        verifyNotTracked("sign");
        verifyNotTracked("encrypt");
    }

    @Test
    void inboundRoutingExceptionRoutesToErrorChannelAndStopsPipeline() {
        when(routingService.routeInbound(any())).thenThrow(new IllegalStateException("route store down"));

        assertTrue(mailInboundChannel.send(inboundMessage("raw".getBytes())));

        Message<?> error = errorChannel.receive(1000);
        assertEquals("route store down", ((Throwable) error.getPayload()).getCause().getMessage());
        assertNull(relayChannel.receive(0));
        assertNull(quarantineChannel.receive(0));
        verifyNotTracked("mail-auth");
    }

    @Test
    void flowSourceNoLongerUsesOldPipelineAbstractions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sealmail/infra/mail/pipeline/MailPipelineFlow.java"));

        assertFalse(source.contains("PipelineResult"));
        assertFalse(source.contains("PipelineStepTracker"));
        assertFalse(source.contains("MailPipelineStep"));
        assertFalse(source.contains("executeMessageWithTracking"));
    }

    @Test
    void quarantineReleaseOutboundUsesSpringIntegrationFlowThroughRouteCryptoAndRelay() {
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("sign", MailProcessingErrorType.SIGNING, "signed".getBytes());
        whenTracked("encrypt", MailProcessingErrorType.ENCRYPTION, "encrypted".getBytes());
        whenTracked("dkim-sign", MailProcessingErrorType.DKIM_SIGNING, "dkim".getBytes());
        whenTracked("relay", MailProcessingErrorType.RELAY, "dkim".getBytes());

        assertTrue(quarantineReleaseChannel.send(releaseMessage("raw".getBytes(), MailDirection.OUTBOUND, false)));

        assertNull(relayChannel.receive(0));
        assertNull(quarantineChannel.receive(0));
        assertNull(errorChannel.receive(0));
        verify(routingService).routeOutbound(any());
        verifyTracked("sign", MailProcessingErrorType.SIGNING);
        verifyTracked("encrypt", MailProcessingErrorType.ENCRYPTION);
        verifyTracked("dkim-sign", MailProcessingErrorType.DKIM_SIGNING);
        verifyTracked("relay", MailProcessingErrorType.RELAY);
        verify(tracker).completeProcessing("processing-1", ProcessingResult.SUCCESS);
    }

    @Test
    void quarantineReleaseInboundWithoutEncryptionRoutesDirectlyToRelayInFlow() {
        byte[] raw = "raw".getBytes();
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("relay", MailProcessingErrorType.RELAY, raw);

        assertTrue(quarantineReleaseChannel.send(releaseMessage(raw, MailDirection.INBOUND, false)));

        verify(routingService).routeInbound(any());
        verifyNotTracked("sign");
        verifyNotTracked("encrypt");
        verifyNotTracked("dkim-sign");
        verifyTracked("relay", MailProcessingErrorType.RELAY);
        verify(tracker).completeProcessing("processing-1", ProcessingResult.SUCCESS);
    }

    @Test
    void quarantineReleaseInboundWithEncryptionEncryptsBeforeRelayInFlow() {
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("encrypt", MailProcessingErrorType.ENCRYPTION, "encrypted".getBytes());
        whenTracked("relay", MailProcessingErrorType.RELAY, "encrypted".getBytes());

        assertTrue(quarantineReleaseChannel.send(releaseMessage("raw".getBytes(), MailDirection.INBOUND, true)));

        verify(routingService).routeInbound(any());
        verifyTracked("encrypt", MailProcessingErrorType.ENCRYPTION);
        verifyTracked("relay", MailProcessingErrorType.RELAY);
        verify(tracker).completeProcessing("processing-1", ProcessingResult.SUCCESS);
    }

    @Test
    void quarantineReleaseStepExceptionRoutesToErrorChannelAndPropagatesToCaller() {
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked("sign", MailProcessingErrorType.SIGNING, "signed".getBytes());
        when(tracker.executeStep(any(), eq("encrypt"), eq(MailProcessingErrorType.ENCRYPTION), any()))
                .thenThrow(new MailProcessingException(MailProcessingErrorType.ENCRYPTION, "encrypt failed", null));

        MessageHandlingException exception = assertThrows(MessageHandlingException.class,
                () -> quarantineReleaseChannel.send(releaseMessage("raw".getBytes(), MailDirection.OUTBOUND, false)));
        assertTrue(exception.getCause() instanceof MailProcessingException);

        Message<?> error = errorChannel.receive(1000);
        assertEquals("encrypt failed", ((Throwable) error.getPayload()).getMessage());
        assertNull(relayChannel.receive(0));
        verifyNotTracked("dkim-sign");
        verifyNotTracked("relay");
    }

    @Test
    void quarantineReleaseRoutingQuarantineRecordsExceptionThroughErrorFlowAndPropagates() {
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withQuarantine(invocation.getArgument(0),
                        "processing-1",
                        "DOMAIN_NOT_CONFIGURED",
                        "domain disabled",
                        MailRecordDisposition.EXCEPTION));

        MessageHandlingException exception = assertThrows(MessageHandlingException.class,
                () -> quarantineReleaseChannel.send(releaseMessage("raw".getBytes(), MailDirection.OUTBOUND, false)));
        assertTrue(exception.getCause() instanceof MailProcessingException);

        Message<?> error = errorChannel.receive(1000);
        Throwable payload = (Throwable) error.getPayload();
        assertEquals("Quarantine release stopped: domain disabled", payload.getMessage());
        assertEquals(MailProcessingErrorType.ROUTING, ((MailProcessingException) payload).errorType());
        verify(tracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verifyNotTracked("sign");
    }

    private void whenTracked(String stepName, MailProcessingErrorType errorType, byte[] payload) {
        when(tracker.executeStep(any(), eq(stepName), eq(errorType), any()))
                .thenAnswer(invocation -> withPayload(invocation.getArgument(0), payload));
    }

    private void whenTrackedQuarantine(String stepName,
                                       MailProcessingErrorType errorType,
                                       String reason,
                                       String detail,
                                       MailRecordDisposition disposition) {
        when(tracker.executeStep(any(), eq(stepName), eq(errorType), any()))
                .thenAnswer(invocation -> withQuarantineContext(
                        invocation.getArgument(0), reason, detail, disposition));
    }

    private void verifyTracked(String stepName, MailProcessingErrorType errorType) {
        verify(tracker).executeStep(any(), eq(stepName), eq(errorType), any());
    }

    private void verifyNotTracked(String stepName) {
        verify(tracker, never()).executeStep(any(), eq(stepName), any(), any());
    }

    private static Message<byte[]> withPayload(Message<byte[]> original, byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders())
                .build();
    }

    private static Message<byte[]> withQuarantineContext(Message<byte[]> original,
                                                         String reason,
                                                         String detail,
                                                         MailRecordDisposition disposition) {
        MailProcessingContext context = context(original)
                .withDecision(context(original).decision().withQuarantine(reason, detail))
                .withRecordDisposition(disposition);
        return MessageBuilder.fromMessage(original)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static MailProcessingContext context(Message<?> message) {
        return (MailProcessingContext) message.getHeaders().get(MailProcessingHeaders.CONTEXT);
    }

    private static Message<byte[]> outboundMessage(byte[] payload) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build();
    }

    private static Message<byte[]> inboundMessage(byte[] payload) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("sender@example.net"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                payload
        );
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, MailProcessingContext.create(envelope))
                .build();
    }

    private static Message<byte[]> releaseMessage(byte[] payload, MailDirection direction, boolean encryptBeforeRelease) {
        MailEnvelope envelope = new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "release",
                Instant.now(),
                payload
        );
        MailProcessingContext context = MailProcessingContext.initial(
                        envelope,
                        direction,
                        "dlp_quarantine_release",
                        payload,
                        "subject",
                        "127.0.0.1")
                .withQuarantineReleaseId("q-1");
        if (encryptBeforeRelease) {
            context = context.withDecision(context.decision()
                    .withEncryptionRequired(true)
                    .withMustEncrypt(true));
        }
        return MessageBuilder.withPayload(payload)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static Message<byte[]> withProcessingId(Message<byte[]> message, String processingId) {
        MailProcessingContext context = context(message).withProcessingId(processingId);
        return MessageBuilder.fromMessage(message)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static Message<byte[]> withQuarantine(Message<byte[]> message,
                                                  String processingId,
                                                  String reason,
                                                  String detail,
                                                  MailRecordDisposition disposition) {
        MailProcessingContext context = context(message)
                .withProcessingId(processingId)
                .withDecision(MailProcessingDecision.none().withQuarantine(reason, detail))
                .withRecordDisposition(disposition);
        return MessageBuilder.fromMessage(message)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static void drain(QueueChannel channel) {
        while (channel.receive(0) != null) {
            // drain
        }
    }

    @Configuration
    @EnableIntegration
    static class Config {

        @Bean
        MessageChannel mailOutboundChannel() {
            return new DirectChannel();
        }

        @Bean
        MessageChannel mailInboundChannel() {
            return new DirectChannel();
        }

        @Bean
        MessageChannel quarantineReleaseChannel() {
            return new DirectChannel();
        }

        @Bean
        QueueChannel relayChannel() {
            return new QueueChannel();
        }

        @Bean
        QueueChannel quarantineChannel() {
            return new QueueChannel();
        }

        @Bean
        QueueChannel errorChannel() {
            return new QueueChannel();
        }

        @Bean
        IntegrationFlow outboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                               MessageChannel mailOutboundChannel,
                                               QueueChannel quarantineChannel,
                                               QueueChannel relayChannel,
                                               QueueChannel errorChannel) {
            return pipelineFlow.outboundFlow(mailOutboundChannel, quarantineChannel, relayChannel, errorChannel);
        }

        @Bean
        IntegrationFlow inboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                              MessageChannel mailInboundChannel,
                                              QueueChannel quarantineChannel,
                                              QueueChannel relayChannel,
                                              QueueChannel errorChannel) {
            return pipelineFlow.inboundFlow(mailInboundChannel, quarantineChannel, relayChannel, errorChannel);
        }

        @Bean
        IntegrationFlow quarantineReleaseProcessingFlow(MailPipelineFlow pipelineFlow,
                                                        MessageChannel quarantineReleaseChannel,
                                                        QueueChannel errorChannel) {
            return pipelineFlow.quarantineReleaseFlow(quarantineReleaseChannel, errorChannel);
        }

        @Bean
        MailPipelineFlow pipelineFlow(DecryptStep decryptStep,
                                      VerifyStep verifyStep,
                                      SignStep signStep,
                                      EncryptStep encryptStep,
                                      QuarantineStep quarantineStep,
                                      RelayStep relayStep,
                                      DlpStep dlpStep,
                                      MailAuthenticationStep mailAuthenticationStep,
                                      DkimSignStep dkimSignStep,
                                      RoutingService routingService,
                                      MailProcessingTracker tracker) {
            return new MailPipelineFlow(
                    decryptStep,
                    verifyStep,
                    signStep,
                    encryptStep,
                    quarantineStep,
                    relayStep,
                    dlpStep,
                    mailAuthenticationStep,
                    dkimSignStep,
                    routingService,
                    tracker);
        }

        @Bean
        DecryptStep decryptStep() {
            return mock(DecryptStep.class);
        }

        @Bean
        VerifyStep verifyStep() {
            return mock(VerifyStep.class);
        }

        @Bean
        SignStep signStep() {
            return mock(SignStep.class);
        }

        @Bean
        EncryptStep encryptStep() {
            return mock(EncryptStep.class);
        }

        @Bean
        QuarantineStep quarantineStep() {
            return mock(QuarantineStep.class);
        }

        @Bean
        RelayStep relayStep() {
            return mock(RelayStep.class);
        }

        @Bean
        DlpStep dlpStep() {
            return mock(DlpStep.class);
        }

        @Bean
        MailAuthenticationStep mailAuthenticationStep() {
            return mock(MailAuthenticationStep.class);
        }

        @Bean
        DkimSignStep dkimSignStep() {
            return mock(DkimSignStep.class);
        }

        @Bean
        RoutingService routingService() {
            return mock(RoutingService.class);
        }

        @Bean
        MailProcessingTracker tracker() {
            return mock(MailProcessingTracker.class);
        }
    }
}
