package com.sealmail.infra.mail.pipeline;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                MessageBuilder.fromMessage(invocation.getArgument(0))
                        .setHeader("processingId", "processing-1")
                        .build());
        when(stepTracker.executeWithTracking(any(), eq(dlpStep)))
                .thenReturn(PipelineResult.success("after-dlp".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(signStep)))
                .thenReturn(PipelineResult.success("signed".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(encryptStep)))
                .thenReturn(PipelineResult.success("encrypted".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(dkimSignStep)))
                .thenReturn(PipelineResult.success("dkim".getBytes()));

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("dkim".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeOutbound(any());
        verify(stepTracker).executeWithTracking(any(), eq(dlpStep));
        verify(stepTracker).executeWithTracking(any(), eq(signStep));
        verify(stepTracker).executeWithTracking(any(), eq(encryptStep));
        verify(stepTracker).executeWithTracking(any(), eq(dkimSignStep));
        verify(stepTracker, never()).completeProcessing("processing-1", ProcessingResult.FAILED);
    }

    @Test
    void outboundDlpQuarantineResultCompletesFailedAndRoutesPipelineResultToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> outbound = outboundMessage(raw);
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                MessageBuilder.fromMessage(invocation.getArgument(0))
                        .setHeader("processingId", "processing-1")
                        .build());
        when(stepTracker.executeWithTracking(any(), eq(dlpStep)))
                .thenReturn(PipelineResult.quarantine(
                        raw,
                        "POLICY_VIOLATION",
                        "DLP QUARANTINE",
                        MailRecordDisposition.DLP_QUARANTINE));

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        PipelineResult result = assertInstanceOf(PipelineResult.class, quarantined.getPayload());
        assertEquals(false, result.success());
        assertEquals("POLICY_VIOLATION", result.quarantineReason());
        assertEquals("DLP QUARANTINE", result.quarantineDetail());
        assertEquals(MailRecordDisposition.DLP_QUARANTINE, result.recordDisposition());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeWithTracking(any(), eq(signStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(encryptStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(dkimSignStep));
    }

    @Test
    void outboundRoutingQuarantineHeaderShortCircuitsStepsAndRoutesRawPayloadToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> outbound = outboundMessage(raw);
        when(routingService.routeOutbound(any())).thenAnswer(invocation ->
                MessageBuilder.fromMessage(invocation.getArgument(0))
                        .setHeader("processingId", "processing-1")
                        .setHeader("quarantineRequired", true)
                        .setHeader("quarantineReason", "DOMAIN_NOT_CONFIGURED")
                        .setHeader("quarantineDetail", "domain disabled")
                        .setHeader("mailRecordDisposition", MailRecordDisposition.EXCEPTION.name())
                        .build());

        assertTrue(mailOutboundChannel.send(outbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        assertArrayEquals(raw, (byte[]) quarantined.getPayload());
        assertEquals("DOMAIN_NOT_CONFIGURED", quarantined.getHeaders().get("quarantineReason"));
        assertEquals("domain disabled", quarantined.getHeaders().get("quarantineDetail"));
        assertEquals(MailRecordDisposition.EXCEPTION.name(), quarantined.getHeaders().get("mailRecordDisposition"));
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeWithTracking(any(), eq(dlpStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(signStep));
    }

    @Test
    void inboundHappyPathRoutesThroughAuthDecryptVerifyDlpThenRelayChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> inbound = inboundMessage(raw);
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                MessageBuilder.fromMessage(invocation.getArgument(0))
                        .setHeader("processingId", "processing-1")
                        .build());
        when(stepTracker.executeWithTracking(any(), eq(mailAuthenticationStep)))
                .thenReturn(PipelineResult.success("authenticated".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(decryptStep)))
                .thenReturn(PipelineResult.success("decrypted".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(verifyStep)))
                .thenReturn(PipelineResult.success("verified".getBytes()));
        when(stepTracker.executeWithTracking(any(), eq(dlpStep)))
                .thenReturn(PipelineResult.success("after-dlp".getBytes()));

        assertTrue(mailInboundChannel.send(inbound));

        Message<?> relayed = relayChannel.receive(1000);
        assertArrayEquals("after-dlp".getBytes(), (byte[]) relayed.getPayload());
        assertNull(quarantineChannel.receive(0));
        verify(routingService).routeInbound(any());
        verify(stepTracker).executeWithTracking(any(), eq(mailAuthenticationStep));
        verify(stepTracker).executeWithTracking(any(), eq(decryptStep));
        verify(stepTracker).executeWithTracking(any(), eq(verifyStep));
        verify(stepTracker).executeWithTracking(any(), eq(dlpStep));
        verify(stepTracker, never()).completeProcessing("processing-1", ProcessingResult.FAILED);
    }

    @Test
    void inboundAuthenticationQuarantineResultCompletesFailedAndRoutesToQuarantineChannel() {
        byte[] raw = "raw".getBytes();
        Message<byte[]> inbound = inboundMessage(raw);
        when(routingService.routeInbound(any())).thenAnswer(invocation ->
                MessageBuilder.fromMessage(invocation.getArgument(0))
                        .setHeader("processingId", "processing-1")
                        .build());
        when(stepTracker.executeWithTracking(any(), eq(mailAuthenticationStep)))
                .thenReturn(PipelineResult.quarantine(
                        raw,
                        "EMAIL_AUTH_FAILED",
                        "spf=FAIL",
                        MailRecordDisposition.EXCEPTION));

        assertTrue(mailInboundChannel.send(inbound));

        Message<?> quarantined = quarantineChannel.receive(1000);
        PipelineResult result = assertInstanceOf(PipelineResult.class, quarantined.getPayload());
        assertEquals(false, result.success());
        assertEquals("EMAIL_AUTH_FAILED", result.quarantineReason());
        assertEquals("spf=FAIL", result.quarantineDetail());
        assertNull(relayChannel.receive(0));
        verify(stepTracker).completeProcessing("processing-1", ProcessingResult.FAILED);
        verify(stepTracker, never()).executeWithTracking(any(), eq(decryptStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(verifyStep));
        verify(stepTracker, never()).executeWithTracking(any(), eq(dlpStep));
    }

    private static Message<byte[]> outboundMessage(byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.com"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
                .build();
    }

    private static Message<byte[]> inboundMessage(byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("mailEnvelope", new MailEnvelope(
                        "msg-" + UUID.randomUUID() + "@example.com",
                        new EmailAddress("sender@example.net"),
                        List.of(new EmailAddress("recipient@example.com")),
                        "127.0.0.1",
                        "helo",
                        Instant.now(),
                        payload
                ))
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
