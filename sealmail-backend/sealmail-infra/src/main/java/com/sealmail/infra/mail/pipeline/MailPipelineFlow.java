package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
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
import org.springframework.messaging.support.ErrorMessage;
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
                                       MessageChannel relayChannel,
                                       MessageChannel errorChannel) {
        return IntegrationFlow.from(mailInboundChannel)
                .handle(Message.class, (message, headers) -> runFlowAction(
                        message,
                        errorChannel,
                        MailProcessingErrorType.ROUTING,
                        typedMessage -> routingService.routeInbound(typedMessage)))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping("error", "nullChannel")
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) ->
                                                runStep(message, mailAuthenticationStep, errorChannel))
                                        .route(Message.class, this::deliveryRoute,
                                                authMapping -> authMapping
                                                        .subFlowMapping(RELAY_ROUTE, authSf -> authSf
                                                                .handle(Message.class, (message, headers) ->
                                                                        runStep(message, decryptStep, errorChannel))
                                                                .route(Message.class, this::deliveryRoute,
                                                                        decryptMapping -> decryptMapping
                                                                                .subFlowMapping(RELAY_ROUTE, decryptSf -> decryptSf
                                                                                        .handle(Message.class, (message, headers) ->
                                                                                                runStep(message, verifyStep, errorChannel))
                                                                                        .route(Message.class, this::deliveryRoute,
                                                        verifyMapping -> verifyMapping
                                                                                                        .subFlowMapping(RELAY_ROUTE, verifySf -> verifySf
                                                                                                                .handle(Message.class, (message, headers) ->
                                                                                                                        runStep(message, dlpStep, errorChannel))
                                                                                                                .route(Message.class, this::deliveryRoute,
                                                                                                                        dlpMapping -> dlpMapping
                                                                                                                                .channelMapping(RELAY_ROUTE, relayChannel)
                                                                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                                                                .defaultOutputChannel("nullChannel")))
                                                                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                                        .defaultOutputChannel("nullChannel")))
                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                .defaultOutputChannel("nullChannel")))
                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                        .defaultOutputChannel("nullChannel")))
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    public IntegrationFlow outboundFlow(MessageChannel mailOutboundChannel,
                                        MessageChannel quarantineChannel,
                                        MessageChannel relayChannel,
                                        MessageChannel errorChannel) {
        return IntegrationFlow.from(mailOutboundChannel)
                .handle(Message.class, (message, headers) -> runFlowAction(
                        message,
                        errorChannel,
                        MailProcessingErrorType.ROUTING,
                        typedMessage -> routingService.routeOutbound(typedMessage)))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping("error", "nullChannel")
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) ->
                                                runStep(message, dlpStep, errorChannel))
                                        .route(Message.class, this::deliveryRoute,
                                                dlpMapping -> dlpMapping
                                                        .subFlowMapping(RELAY_ROUTE, dlpSf -> dlpSf
                                                                .handle(Message.class, (message, headers) ->
                                                                        runStep(message, signStep, errorChannel))
                                                                .route(Message.class, this::deliveryRoute,
                                                                        signMapping -> signMapping
                                                                                .subFlowMapping(RELAY_ROUTE, signSf -> signSf
                                                                                        .handle(Message.class, (message, headers) ->
                                                                                                runStep(message, encryptStep, errorChannel))
                                                                                        .route(Message.class, this::deliveryRoute,
                                                                                                encryptMapping -> encryptMapping
                                                                                                        .subFlowMapping(RELAY_ROUTE, encryptSf -> encryptSf
                                                                                                                .handle(Message.class, (message, headers) ->
                                                                                                                        runStep(message, dkimSignStep, errorChannel))
                                                                                                                .route(Message.class, this::deliveryRoute,
                                                                                                                        dkimMapping -> dkimMapping
                                                                                                                                .channelMapping(RELAY_ROUTE, relayChannel)
                                                                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                                                                .defaultOutputChannel("nullChannel")))
                                                                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                                        .defaultOutputChannel("nullChannel")))
                                                                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                                                .defaultOutputChannel("nullChannel")))
                                                        .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                                        .defaultOutputChannel("nullChannel")))
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel, MessageChannel errorChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle(Message.class, (message, headers) ->
                        runStep(message, quarantineStep, errorChannel))
                .nullChannel();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel,
                                     MessageChannel quarantineChannel,
                                     MessageChannel errorChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle(Message.class, (message, headers) -> runStep(message, relayStep, errorChannel))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping("error", "nullChannel")
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) -> completeSuccess(message))
                                        .nullChannel())
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
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

    private Message<byte[]> runStep(Object message, MailPipelineStep step, MessageChannel errorChannel) {
        return runFlowAction(
                message,
                errorChannel,
                errorType(step),
                typedMessage -> stepTracker.executeMessageWithTracking(typedMessage, step));
    }

    private Message<byte[]> runFlowAction(Object message,
                                          MessageChannel errorChannel,
                                          MailProcessingErrorType errorType,
                                          StepAction action) {
        Message<byte[]> typedMessage = byteMessage(message);
        try {
            return action.apply(typedMessage);
        } catch (Exception e) {
            sendError(errorChannel, typedMessage, errorType, e);
            return markErrorHandled(typedMessage);
        }
    }

    private void sendError(MessageChannel errorChannel,
                           Message<byte[]> message,
                           MailProcessingErrorType errorType,
                           Exception error) {
        Throwable throwable = error instanceof MailProcessingException
                ? error
                : new MailProcessingException(errorType, error.getMessage(), context(message.getHeaders()), error);
        errorChannel.send(new ErrorMessage(throwable, message));
    }

    private Message<byte[]> markErrorHandled(Message<byte[]> message) {
        return MessageBuilder.fromMessage(message)
                .setHeader(MailProcessingHeaders.ERROR_HANDLED, true)
                .build();
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
        if (Boolean.TRUE.equals(message.getHeaders().get(MailProcessingHeaders.ERROR_HANDLED))) {
            return "error";
        }
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

    private MailProcessingErrorType errorType(MailPipelineStep step) {
        if (step == null) {
            return MailProcessingErrorType.UNKNOWN;
        }
        String stepName = step.getStepName();
        if (stepName == null || stepName.isBlank()) {
            return MailProcessingErrorType.UNKNOWN;
        }
        return switch (stepName) {
            case "mail-auth" -> MailProcessingErrorType.AUTHENTICATION;
            case "decrypt" -> MailProcessingErrorType.DECRYPTION;
            case "verify-signature" -> MailProcessingErrorType.VERIFICATION;
            case "dlp" -> MailProcessingErrorType.DLP;
            case "sign" -> MailProcessingErrorType.SIGNING;
            case "encrypt" -> MailProcessingErrorType.ENCRYPTION;
            case "dkim-sign" -> MailProcessingErrorType.DKIM_SIGNING;
            case "relay" -> MailProcessingErrorType.RELAY;
            case "quarantine" -> MailProcessingErrorType.QUARANTINE;
            default -> MailProcessingErrorType.PIPELINE;
        };
    }

    @FunctionalInterface
    private interface StepAction {
        Message<byte[]> apply(Message<byte[]> message);
    }
}
