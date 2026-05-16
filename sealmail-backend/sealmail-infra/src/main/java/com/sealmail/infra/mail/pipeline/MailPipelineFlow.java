package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.infra.mail.pipeline.step.DecryptStep;
import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.DlpStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.MailAuthenticationStep;
import com.sealmail.infra.mail.pipeline.step.QuarantineStep;
import com.sealmail.infra.mail.pipeline.step.RelayStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import com.sealmail.infra.mail.pipeline.step.VerifyStep;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class MailPipelineFlow {

    private static final String RELAY_ROUTE = "relay";
    private static final String QUARANTINE_ROUTE = "quarantine";

    private final DecryptStep decryptStep;
    private final VerifyStep verifyStep;
    private final SignStep signStep;
    private final EncryptStep encryptStep;
    private final QuarantineStep quarantineStep;
    private final RelayStep relayStep;
    private final DlpStep dlpStep;
    private final MailAuthenticationStep mailAuthenticationStep;
    private final DkimSignStep dkimSignStep;
    private final RoutingService routingService;
    private final PipelineStepTracker stepTracker;

    public MailPipelineFlow(DecryptStep decryptStep,
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
        this.decryptStep = decryptStep;
        this.verifyStep = verifyStep;
        this.signStep = signStep;
        this.encryptStep = encryptStep;
        this.quarantineStep = quarantineStep;
        this.relayStep = relayStep;
        this.dlpStep = dlpStep;
        this.mailAuthenticationStep = mailAuthenticationStep;
        this.dkimSignStep = dkimSignStep;
        this.routingService = routingService;
        this.stepTracker = stepTracker;
    }

    public IntegrationFlow inboundFlow(MessageChannel mailInboundChannel,
                                       MessageChannel quarantineChannel,
                                       MessageChannel relayChannel) {
        return IntegrationFlow.from(mailInboundChannel)
                .handle(Message.class, (message, headers) -> routingService.routeInbound(byteMessage(message)))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) -> runStep(message, mailAuthenticationStep))
                                        .route(Message.class, this::deliveryRoute,
                                                authMapping -> authMapping
                                                        .subFlowMapping(RELAY_ROUTE, authSf -> authSf
                                                                .handle(Message.class, (message, headers) -> runStep(message, decryptStep))
                                                                .route(Message.class, this::deliveryRoute,
                                                                        decryptMapping -> decryptMapping
                                                                                .subFlowMapping(RELAY_ROUTE, decryptSf -> decryptSf
                                                                                        .handle(Message.class, (message, headers) -> runStep(message, verifyStep))
                                                                                        .route(Message.class, this::deliveryRoute,
                                                                                                verifyMapping -> verifyMapping
                                                                                                        .subFlowMapping(RELAY_ROUTE, verifySf -> verifySf
                                                                                                                .handle(Message.class, (message, headers) -> runStep(message, dlpStep))
                                                                                                                .route(Message.class, this::deliveryRoute,
                                                                                                                        dlpMapping -> dlpMapping
                                                                                                                                .channelMapping(RELAY_ROUTE, relayChannel)
                                                                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel))
                .get();
    }

    public IntegrationFlow outboundFlow(MessageChannel mailOutboundChannel,
                                        MessageChannel quarantineChannel,
                                        MessageChannel relayChannel) {
        return IntegrationFlow.from(mailOutboundChannel)
                .handle(Message.class, (message, headers) -> routingService.routeOutbound(byteMessage(message)))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) -> runStep(message, dlpStep))
                                        .route(Message.class, this::deliveryRoute,
                                                dlpMapping -> dlpMapping
                                                        .subFlowMapping(RELAY_ROUTE, dlpSf -> dlpSf
                                                                .handle(Message.class, (message, headers) -> runStep(message, signStep))
                                                                .route(Message.class, this::deliveryRoute,
                                                                        signMapping -> signMapping
                                                                                .subFlowMapping(RELAY_ROUTE, signSf -> signSf
                                                                                        .handle(Message.class, (message, headers) -> runStep(message, encryptStep))
                                                                                        .route(Message.class, this::deliveryRoute,
                                                                                                encryptMapping -> encryptMapping
                                                                                                        .subFlowMapping(RELAY_ROUTE, encryptSf -> encryptSf
                                                                                                                .handle(Message.class, (message, headers) -> runStep(message, dkimSignStep))
                                                                                                                .route(Message.class, this::deliveryRoute,
                                                                                                                        dkimMapping -> dkimMapping
                                                                                                                                .channelMapping(RELAY_ROUTE, relayChannel)
                                                                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)))
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel))
                .get();
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle(Message.class, (message, headers) -> runStep(message, quarantineStep))
                .nullChannel();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel, MessageChannel quarantineChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle(Message.class, (message, headers) -> runStep(message, relayStep))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) -> completeSuccess(message))
                                        .nullChannel())
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel))
                .get();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle(Message.class, (message, headers) -> runStep(message, relayStep))
                .nullChannel();
    }

    private Message<byte[]> runStep(Object message, MailPipelineStep step) {
        return stepTracker.executeMessageWithTracking(byteMessage(message), step);
    }

    private Object completeSuccess(Object message) {
        Message<byte[]> typedMessage = byteMessage(message);
        stepTracker.completeProcessing(processingId(typedMessage.getHeaders()), ProcessingResult.SUCCESS);
        return typedMessage;
    }

    private Message<byte[]> byteMessage(Object message) {
        if (message instanceof Message<?> typedMessage) {
            Object payload = typedMessage.getPayload();
            if (!(payload instanceof byte[] bytes)) {
                throw new IllegalArgumentException("Mail pipeline payload must be byte[]");
            }
            return MessageBuilder.withPayload(bytes)
                    .copyHeaders(typedMessage.getHeaders())
                    .build();
        }
        if (message instanceof byte[] bytes) {
            return MessageBuilder.withPayload(bytes).build();
        }
        throw new IllegalArgumentException("Mail pipeline message must be a Spring Message<byte[]> or byte[]");
    }

    private String deliveryRoute(Message<?> message) {
        MailProcessingContext context = context(message.getHeaders());
        if (context != null && context.decision().requiresQuarantine()) {
            stepTracker.completeProcessing(context.processingId(), ProcessingResult.FAILED);
            return QUARANTINE_ROUTE;
        }
        return RELAY_ROUTE;
    }

    private MailProcessingContext context(MessageHeaders headers) {
        Object value = headers.get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private String processingId(MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        return context != null ? context.processingId() : null;
    }
}
