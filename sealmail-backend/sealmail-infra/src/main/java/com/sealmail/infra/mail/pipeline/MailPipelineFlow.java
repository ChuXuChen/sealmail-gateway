package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
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
    private final MailProcessingTracker tracker;

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
                            MailProcessingTracker tracker) {
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
        this.tracker = tracker;
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
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "mail-auth", MailProcessingErrorType.AUTHENTICATION,
                                errorChannel, mailAuthenticationStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "decrypt", MailProcessingErrorType.DECRYPTION,
                                errorChannel, decryptStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "verify-signature", MailProcessingErrorType.VERIFICATION,
                                errorChannel, verifyStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "dlp", MailProcessingErrorType.DLP,
                                errorChannel, dlpStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(RELAY_ROUTE, relayChannel)
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
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "dlp", MailProcessingErrorType.DLP,
                                errorChannel, dlpStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "sign", MailProcessingErrorType.SIGNING,
                                errorChannel, signStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "encrypt", MailProcessingErrorType.ENCRYPTION,
                                errorChannel, encryptStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputToParentFlow())
                .handle(Message.class, (message, headers) ->
                        runStep(message, "dkim-sign", MailProcessingErrorType.DKIM_SIGNING,
                                errorChannel, dkimSignStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .channelMapping(RELAY_ROUTE, relayChannel)
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel, MessageChannel errorChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle(Message.class, (message, headers) ->
                        runStep(message, "quarantine", MailProcessingErrorType.QUARANTINE,
                                errorChannel, quarantineStep::execute))
                .nullChannel();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel,
                                     MessageChannel quarantineChannel,
                                     MessageChannel errorChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle(Message.class, (message, headers) ->
                        runStep(message, "relay", MailProcessingErrorType.RELAY, errorChannel, relayStep::execute))
                .route(Message.class, this::deliveryRoute,
                        mapping -> mapping
                                .subFlowMapping(RELAY_ROUTE, sf -> sf
                                        .handle(Message.class, (message, headers) -> completeSuccess(message))
                                        .nullChannel())
                                .channelMapping(QUARANTINE_ROUTE, quarantineChannel)
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    public IntegrationFlow quarantineReleaseFlow(MessageChannel quarantineReleaseChannel,
                                                 MessageChannel errorChannel) {
        return IntegrationFlow.from(quarantineReleaseChannel)
                .handle(Message.class, (message, headers) -> runReleaseFlowAction(
                        message,
                        errorChannel,
                        MailProcessingErrorType.ROUTING,
                        this::routeReleasedMail))
                .handle(Message.class, (message, headers) ->
                        failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.ROUTING))
                .route(Message.class, this::releaseDirectionRoute,
                        mapping -> mapping
                                .subFlowMapping("outbound", sf -> sf
                                        .handle(Message.class, (message, headers) ->
                                                runReleaseStep(message, "sign", MailProcessingErrorType.SIGNING,
                                                        errorChannel, signStep::execute))
                                        .handle(Message.class, (message, headers) ->
                                                failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.SIGNING))
                                        .handle(Message.class, (message, headers) ->
                                                runReleaseStep(message, "encrypt", MailProcessingErrorType.ENCRYPTION,
                                                        errorChannel, encryptStep::execute))
                                        .handle(Message.class, (message, headers) ->
                                                failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.ENCRYPTION))
                                        .handle(Message.class, (message, headers) ->
                                                runReleaseStep(message, "dkim-sign", MailProcessingErrorType.DKIM_SIGNING,
                                                        errorChannel, dkimSignStep::execute))
                                        .handle(Message.class, (message, headers) ->
                                                failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.DKIM_SIGNING))
                                        .handle(Message.class, (message, headers) ->
                                                runReleaseStep(message, "relay", MailProcessingErrorType.RELAY,
                                                        errorChannel, relayStep::execute))
                                        .handle(Message.class, (message, headers) ->
                                                failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.RELAY))
                                        .handle(Message.class, (message, headers) -> completeSuccess(message))
                                        .nullChannel())
                                .subFlowMapping("inbound", sf -> sf
                                        .route(Message.class, this::inboundReleaseRoute,
                                                inboundMapping -> inboundMapping
                                                        .subFlowMapping("encrypt", encryptSf -> encryptSf
                                                                .handle(Message.class, (message, headers) ->
                                                                        runReleaseStep(message, "encrypt", MailProcessingErrorType.ENCRYPTION,
                                                                                errorChannel, encryptStep::execute))
                                                                .handle(Message.class, (message, headers) ->
                                                                        failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.ENCRYPTION))
                                                                .handle(Message.class, (message, headers) ->
                                                                        runReleaseStep(message, "relay", MailProcessingErrorType.RELAY,
                                                                                errorChannel, relayStep::execute))
                                                                .handle(Message.class, (message, headers) ->
                                                                        failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.RELAY))
                                                                .handle(Message.class, (message, headers) -> completeSuccess(message))
                                                                .nullChannel())
                                                        .subFlowMapping(RELAY_ROUTE, relaySf -> relaySf
                                                                .handle(Message.class, (message, headers) ->
                                                                        runReleaseStep(message, "relay", MailProcessingErrorType.RELAY,
                                                                                errorChannel, relayStep::execute))
                                                                .handle(Message.class, (message, headers) ->
                                                                        failReleaseIfQuarantined(message, errorChannel, MailProcessingErrorType.RELAY))
                                                                .handle(Message.class, (message, headers) -> completeSuccess(message))
                                                                .nullChannel())
                                                        .defaultOutputChannel("nullChannel")))
                                .defaultOutputChannel("nullChannel"))
                .get();
    }

    private Message<byte[]> routeReleasedMail(Message<byte[]> message) {
        MailProcessingContext context = context(message.getHeaders());
        if (context != null && context.direction() == MailDirection.INBOUND) {
            return routingService.routeInbound(message);
        }
        return routingService.routeOutbound(message);
    }

    private String releaseDirectionRoute(Message<?> message) {
        MailProcessingContext context = context(message.getHeaders());
        if (context != null && context.direction() == MailDirection.INBOUND) {
            return "inbound";
        }
        return "outbound";
    }

    private String inboundReleaseRoute(Message<?> message) {
        MailProcessingContext context = context(message.getHeaders());
        if (context != null
                && (context.decision().encryptionRequired() || context.decision().mustEncrypt())) {
            return "encrypt";
        }
        return RELAY_ROUTE;
    }

    private Message<byte[]> runReleaseStep(Object message,
                                           String stepName,
                                           MailProcessingErrorType errorType,
                                           MessageChannel errorChannel,
                                           MailProcessingTracker.StepAction action) {
        return runReleaseFlowAction(
                message,
                errorChannel,
                errorType,
                typedMessage -> tracker.executeStep(typedMessage, stepName, errorType, action));
    }

    private Message<byte[]> failReleaseIfQuarantined(Object message,
                                                     MessageChannel errorChannel,
                                                     MailProcessingErrorType errorType) {
        Message<byte[]> typedMessage = byteMessage(message);
        MailProcessingContext context = context(typedMessage.getHeaders());
        if (context == null || !context.decision().requiresQuarantine()) {
            return typedMessage;
        }
        tracker.completeProcessing(context.processingId(), ProcessingResult.FAILED);
        MailProcessingException exception = new MailProcessingException(
                errorType,
                "Quarantine release stopped: " + releaseQuarantineDetail(context),
                context);
        sendError(errorChannel, typedMessage, exception);
        throw exception;
    }

    private String releaseQuarantineDetail(MailProcessingContext context) {
        if (context == null || context.decision().quarantine() == null) {
            return "released mail cannot be delivered";
        }
        String detail = context.decision().quarantine().detail();
        return detail != null && !detail.isBlank()
                ? detail
                : context.decision().quarantine().reason();
    }

    private Message<byte[]> runStep(Object message,
                                    String stepName,
                                    MailProcessingErrorType errorType,
                                    MessageChannel errorChannel,
                                    MailProcessingTracker.StepAction action) {
        return runFlowAction(
                message,
                errorChannel,
                errorType,
                typedMessage -> tracker.executeStep(typedMessage, stepName, errorType, action));
    }

    private Message<byte[]> runFlowAction(Object message,
                                          MessageChannel errorChannel,
                                          MailProcessingErrorType errorType,
                                          MailProcessingTracker.StepAction action) {
        Message<byte[]> typedMessage = byteMessage(message);
        try {
            return action.apply(typedMessage);
        } catch (Exception e) {
            MailProcessingException exception = mailProcessingException(typedMessage, errorType, e);
            sendError(errorChannel, typedMessage, exception);
            throw exception;
        }
    }

    private Message<byte[]> runReleaseFlowAction(Object message,
                                                 MessageChannel errorChannel,
                                                 MailProcessingErrorType errorType,
                                                 MailProcessingTracker.StepAction action) {
        Message<byte[]> typedMessage = byteMessage(message);
        try {
            return action.apply(typedMessage);
        } catch (Exception e) {
            MailProcessingException exception = mailProcessingException(typedMessage, errorType, e);
            sendError(errorChannel, typedMessage, exception);
            throw exception;
        }
    }

    private void sendError(MessageChannel errorChannel,
                           Message<byte[]> message,
                           MailProcessingException error) {
        errorChannel.send(new ErrorMessage(error, message));
    }

    private MailProcessingException mailProcessingException(Message<byte[]> message,
                                                           MailProcessingErrorType errorType,
                                                           Exception error) {
        if (error instanceof MailProcessingException mailProcessingException) {
            return mailProcessingException;
        }
        return new MailProcessingException(errorType, error.getMessage(), context(message.getHeaders()), error);
    }

    private Object completeSuccess(Object message) {
        Message<byte[]> typedMessage = byteMessage(message);
        tracker.completeProcessing(processingId(typedMessage.getHeaders()), ProcessingResult.SUCCESS);
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
            tracker.completeProcessing(context.processingId(), ProcessingResult.FAILED);
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
