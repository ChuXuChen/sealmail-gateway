package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.mailsecurity.MailEnvelope;
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
    QueueChannel relayChannel;

    @Autowired
    QueueChannel quarantineChannel;

    @Autowired
    RoutingService routingService;

    @Autowired
    PipelineStepTracker stepTracker;

    @Autowired
    DlpStep dlpStep;

    @Autowired
    SignStep signStep;

    @Autowired
    EncryptStep encryptStep;

    @Autowired
    DkimSignStep dkimSignStep;

    @Autowired
    MailAuthenticationStep mailAuthenticationStep;

    @Autowired
    DecryptStep decryptStep;

    @Autowired
    VerifyStep verifyStep;

    @BeforeEach
    void resetMocksAndChannels() {
        reset(routingService, stepTracker, dlpStep, signStep, encryptStep, dkimSignStep,
                mailAuthenticationStep, decryptStep, verifyStep);
        drain(relayChannel);
        drain(quarantineChannel);
    }

    @Test
    void outboundHappyPathRoutesThroughDlpSignEncryptDkimThenRelayChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> outbound = outboundMessage(raw);
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked(dlpStep, PipelineResult.success("after-dlp".getBytes()));
        whenTracked(signStep, PipelineResult.success("signed".getBytes()));
        whenTracked(encryptStep, PipelineResult.success("encrypted".getBytes()));
        whenTracked(dkimSignStep, PipelineResult.success("dkim".getBytes()));

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("dkim".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeOutbound(any());
        verify(stepTracker).executeMessageWithTracking(any(), eq(dlpStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(signStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(encryptStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(dkimSignStep));
        verify(stepTracker, never()).completeProcessing("processing-1", ProcessingResult.FAILED);
    }

    @Test
    void outboundDlpQuarantineResultCompletesFailedAndRoutesContextMessageToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> outbound = outboundMessage(raw);
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked(dlpStep, PipelineResult.quarantine(
                raw,
                "POLICY_VIOLATION",
                "DLP QUARANTINE",
                MailRecordDisposition.DLP_QUARANTINE));

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("POLICY_VIOLATION", context.decision().quarantine().reason());
        assertEquals("DLP QUARANTINE", context.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.DLP_QUARANTINE, context.recordDisposition());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(signStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(encryptStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(dkimSignStep));
    }

    @Test
    void outboundRoutingQuarantineContextShortCircuitsStepsAndRoutesRawPayloadToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> outbound = outboundMessage(raw);
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                withQuarantine(invocation.getArgument(0),
                        "processing-1",
                        "DOMAIN_NOT_CONFIGURED",
                        "domain disabled",
                        MailRecordDisposition.EXCEPTION));

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("DOMAIN_NOT_CONFIGURED", context.decision().quarantine().reason());
        assertEquals("domain disabled", context.decision().quarantine().detail());
        assertEquals(MailRecordDisposition.EXCEPTION, context.recordDisposition());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(dlpStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(signStep));
    }

    @Test
    void inboundHappyPathRoutesThroughAuthDecryptVerifyDlpThenRelayChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> inbound = inboundMessage(raw);
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked(mailAuthenticationStep, PipelineResult.success("authenticated".getBytes()));
        whenTracked(decryptStep, PipelineResult.success("decrypted".getBytes()));
        whenTracked(verifyStep, PipelineResult.success("verified".getBytes()));
        whenTracked(dlpStep, PipelineResult.success("after-dlp".getBytes()));

        assertTrue(mailInboundChannel.send(inbound));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("after-dlp".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeInbound(any());
        verify(stepTracker).executeMessageWithTracking(any(), eq(mailAuthenticationStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(decryptStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(verifyStep));
        verify(stepTracker).executeMessageWithTracking(any(), eq(dlpStep));
        verify(stepTracker, never()).completeProcessing("processing-1", ProcessingResult.FAILED);
    }

    @Test
    void inboundAuthenticationQuarantineResultCompletesFailedAndRoutesToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> inbound = inboundMessage(raw);
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked(mailAuthenticationStep, PipelineResult.quarantine(
                raw,
                "EMAIL_AUTH_FAILED",
                "spf=FAIL",
                MailRecordDisposition.EXCEPTION));

        assertTrue(mailInboundChannel.send(inbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("EMAIL_AUTH_FAILED", context.decision().quarantine().reason());
        assertEquals("spf=FAIL", context.decision().quarantine().detail());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(decryptStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(verifyStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(dlpStep));
    }

    @Test
    void inboundDecryptQuarantineResultShortCircuitsVerifyAndDlp() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> inbound = inboundMessage(raw);
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                withProcessingId(invocation.getArgument(0), "processing-1"));
        whenTracked(mailAuthenticationStep, PipelineResult.success("authenticated".getBytes()));
        whenTracked(decryptStep, PipelineResult.quarantine(
                raw,
                "DECRYPT_FAILED",
                "recipient key unavailable",
                MailRecordDisposition.EXCEPTION));

        assertTrue(mailInboundChannel.send(inbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        MailProcessingContext context = context(quarantined);
        assertEquals("DECRYPT_FAILED", context.decision().quarantine().reason());
        assertEquals("recipient key unavailable", context.decision().quarantine().detail());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(verifyStep));
        verify(stepTracker, never()).executeMessageWithTracking(any(), eq(dlpStep));
    }

    @Test
    void flowSourceNoLongerRoutesOnPipelineResultPayload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sealmail/infra/mail/pipeline/MailPipelineFlow.java"));

        assertFalse(source.contains("PipelineResult::success"));
        assertFalse(source.contains("<PipelineResult"));
    }

    @SuppressWarnings("unchecked")
    private void whenTracked(MailPipelineStep step, PipelineResult result) {
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
            MailProcessingContext context = context(builder.build());
            if (context != null) {
                builder.setHeader(MailProcessingHeaders.CONTEXT, context
                        .withDecision(context.decision().withQuarantine(
                                result.quarantineReason() != null ? result.quarantineReason() : "POLICY_VIOLATION",
                                result.quarantineDetail() != null ? result.quarantineDetail() : result.errorMessage()))
                        .withRecordDisposition(result.recordDisposition() != null
                                ? result.recordDisposition()
                                : MailRecordDisposition.EXCEPTION));
            }
        }
        return builder.build();
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

    private static Message<byte[]> withProcessingId(Message<byte[]> message, String processingId) {
        MailProcessingContext context = ((MailProcessingContext) message.getHeaders()
                .get(MailProcessingHeaders.CONTEXT))
                .withProcessingId(processingId);
        return MessageBuilder.fromMessage(message)
                .setHeader(MailProcessingHeaders.CONTEXT, context)
                .build();
    }

    private static Message<byte[]> withQuarantine(Message<byte[]> message,
                                                  String processingId,
                                                  String reason,
                                                  String detail,
                                                  MailRecordDisposition disposition) {
        MailProcessingContext context = ((MailProcessingContext) message.getHeaders()
                .get(MailProcessingHeaders.CONTEXT))
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
        QueueChannel relayChannel() {
            return new QueueChannel();
        }

        @Bean
        QueueChannel quarantineChannel() {
            return new QueueChannel();
        }

        @Bean
        IntegrationFlow outboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                               MessageChannel mailOutboundChannel,
                                               QueueChannel quarantineChannel,
                                               QueueChannel relayChannel) {
            return pipelineFlow.outboundFlow(mailOutboundChannel, quarantineChannel, relayChannel);
        }

        @Bean
        IntegrationFlow inboundProcessingFlow(MailPipelineFlow pipelineFlow,
                                              MessageChannel mailInboundChannel,
                                              QueueChannel quarantineChannel,
                                              QueueChannel relayChannel) {
            return pipelineFlow.inboundFlow(mailInboundChannel, quarantineChannel, relayChannel);
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
                                      PipelineStepTracker stepTracker) {
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
                    stepTracker);
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
        PipelineStepTracker stepTracker() {
            return mock(PipelineStepTracker.class);
        }
    }
}
