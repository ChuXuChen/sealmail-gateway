package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
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
                .handle((payload, headers) -> routingService.routeInbound(byteMessage(payload, headers)))
                .handle((payload, headers) -> quarantineIfRequired(payload, headers, quarantineChannel))
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        byteMessage(payload, headers),
                        mailAuthenticationStep))
                .<PipelineResult, Boolean>route(PipelineResult::success,
                        authMapping -> authMapping
                                .subFlowMapping(true, authSf -> authSf
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                resultMessage(payload, headers),
                                                decryptStep))
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                resultMessage(payload, headers),
                                                verifyStep))
                                        .<PipelineResult, Boolean>route(PipelineResult::success,
                                                mapping -> mapping
                                                        .subFlowMapping(true, sf -> sf
                                                                .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                                        resultMessage(payload, headers),
                                                                        dlpStep))
                                                                .<PipelineResult, Boolean>route(PipelineResult::success,
                                                                        innerMapping -> innerMapping
                                                                                .subFlowMapping(true, innerSf -> innerSf
                                                                                        .handle((payload, headers) ->
                                                                                                resultMessage(payload, headers))
                                                                                        .channel(relayChannel))
                                                                                .subFlowMapping(false, innerSf -> innerSf
                                                                                        .handle((p, h) -> completeFailed(h, p))
                                                                                        .channel(quarantineChannel))))
                                                        .subFlowMapping(false, sf -> sf
                                                                .handle((p, h) -> completeFailed(h, p))
                                                                .channel(quarantineChannel))))
                                .subFlowMapping(false, authSf -> authSf
                                        .handle((p, h) -> completeFailed(h, p))
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow outboundFlow(MessageChannel mailOutboundChannel,
                                        MessageChannel quarantineChannel,
                                        MessageChannel relayChannel) {
        return IntegrationFlow.from(mailOutboundChannel)
                .handle((payload, headers) -> routingService.routeOutbound(byteMessage(payload, headers)))
                .handle((payload, headers) -> quarantineIfRequired(payload, headers, quarantineChannel))
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        byteMessage(payload, headers),
                        dlpStep))
                .<PipelineResult, Boolean>route(PipelineResult::success,
                        dlpMapping -> dlpMapping
                                .subFlowMapping(true, dlpSf -> dlpSf
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                resultMessage(payload, headers),
                                                signStep))
                                        .<PipelineResult, Boolean>route(PipelineResult::success,
                                                mapping -> mapping
                                                        .subFlowMapping(true, sf -> sf
                                                                .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                                        resultMessage(payload, headers),
                                                                        encryptStep))
                                                                .<PipelineResult, Boolean>route(PipelineResult::success,
                                                                        innerMapping -> innerMapping
                                                                                .subFlowMapping(true, innerSf -> innerSf
                                                                                        .handle((payload, headers) ->
                                                                                                resultMessage(payload, headers))
                                                                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                                                                byteMessage(payload, headers),
                                                                                                dkimSignStep))
                                                                                        .handle((payload, headers) ->
                                                                                                resultMessage(payload, headers))
                                                                                        .channel(relayChannel))
                                                                                .subFlowMapping(false, innerSf -> innerSf
                                                                                        .handle((p, h) -> completeFailed(h, p))
                                                                                        .channel(quarantineChannel))))
                                                        .subFlowMapping(false, sf -> sf
                                                                .handle((p, h) -> completeFailed(h, p))
                                                                .channel(quarantineChannel))))
                                .subFlowMapping(false, dlpSf -> dlpSf
                                        .handle((p, h) -> completeFailed(h, p))
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle((payload, headers) -> {
                    if (payload instanceof PipelineResult result) {
                        MailProcessingContext context = quarantineContext(result, headers);
                        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(quarantinePayload(result, headers))
                                .copyHeaders(headers);
                        if (context != null) {
                            builder.setHeader(MailProcessingHeaders.CONTEXT, context);
                        }
                        return stepTracker.executeWithTracking(
                                builder.build(),
                                quarantineStep);
                    }
                    return stepTracker.executeWithTracking(byteMessage(payload, headers), quarantineStep);
                })
                .nullChannel();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel, MessageChannel quarantineChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        byteMessage(payload, headers),
                        relayStep))
                .<PipelineResult, Boolean>route(PipelineResult::success,
                        mapping -> mapping
                                .subFlowMapping(true, sf -> sf
                                        .handle((p, h) -> {
                                            String processingId = processingId(h);
                                            stepTracker.completeProcessing(processingId, ProcessingResult.SUCCESS);
                                            return p;
                                        })
                                        .nullChannel())
                                .subFlowMapping(false, sf -> sf
                                        .handle((p, h) -> completeFailed(h, p))
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        byteMessage(payload, headers),
                        relayStep))
                .nullChannel();
    }

    private Message<byte[]> byteMessage(Object payload, MessageHeaders headers) {
        return MessageBuilder.withPayload((byte[]) payload)
                .copyHeaders(headers)
                .build();
    }

    private Message<byte[]> resultMessage(Object payload, MessageHeaders headers) {
        PipelineResult result = (PipelineResult) payload;
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(result.payload())
                .copyHeaders(headers);
        if (result.headers() != null) {
            Object context = result.headers().get(MailProcessingHeaders.CONTEXT);
            if (context instanceof MailProcessingContext mailProcessingContext) {
                builder.setHeader(MailProcessingHeaders.CONTEXT, mailProcessingContext);
            }
        }
        return builder.build();
    }

    private Object completeFailed(MessageHeaders headers, Object payload) {
        String processingId = processingId(headers);
        stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
        return payload;
    }

    private Message<byte[]> quarantineIfRequired(Object payload,
                                                 MessageHeaders headers,
                                                 MessageChannel quarantineChannel) {
        byte[] mailPayload = payload instanceof byte[] bytes ? bytes : new byte[0];
        if (!quarantineRequired(headers)) {
            return MessageBuilder.withPayload(mailPayload)
                    .copyHeaders(headers)
                    .build();
        }

        String processingId = processingId(headers);
        stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
        quarantineChannel.send(MessageBuilder
                .withPayload(mailPayload)
                .copyHeaders(headers)
                .build());
        return null;
    }

    private byte[] quarantinePayload(PipelineResult result, MessageHeaders headers) {
        if (result.payload() != null && result.payload().length > 0) {
            return result.payload();
        }
        MailProcessingContext context = context(headers);
        if (context != null && context.originalMailContent().length > 0) {
            return context.originalMailContent();
        }
        return new byte[0];
    }

    private MailProcessingContext quarantineContext(PipelineResult result, MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        if (context == null) {
            return null;
        }
        return context
                .withDecision(context.decision().withQuarantine(
                        quarantineReason(result, headers),
                        quarantineDetail(result, headers)))
                .withRecordDisposition(recordDisposition(result, headers));
    }

    private String quarantineReason(PipelineResult result, MessageHeaders headers) {
        if (result.quarantineReason() != null && !result.quarantineReason().isBlank()) {
            return result.quarantineReason();
        }
        String contextReason = quarantineReason(headers);
        return contextReason != null && !contextReason.isBlank()
                ? contextReason
                : "POLICY_VIOLATION";
    }

    private String quarantineDetail(PipelineResult result, MessageHeaders headers) {
        if (result.quarantineDetail() != null && !result.quarantineDetail().isBlank()) {
            return result.quarantineDetail();
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        String contextDetail = quarantineDetail(headers);
        return contextDetail != null && !contextDetail.isBlank()
                ? contextDetail
                : quarantineReason(result, headers);
    }

    private MailRecordDisposition recordDisposition(PipelineResult result, MessageHeaders headers) {
        if (result.recordDisposition() != null) {
            return result.recordDisposition();
        }
        MailProcessingContext context = context(headers);
        if (context != null && context.recordDisposition() != null) {
            return context.recordDisposition();
        }
        return MailRecordDisposition.EXCEPTION;
    }

    private MailProcessingContext context(MessageHeaders headers) {
        Object value = headers.get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private String processingId(MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        return context != null ? context.processingId() : null;
    }

    private boolean quarantineRequired(MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        return context != null && context.decision().requiresQuarantine();
    }

    private String quarantineReason(MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        if (context == null || context.decision().quarantine() == null) {
            return null;
        }
        return context.decision().quarantine().reason();
    }

    private String quarantineDetail(MessageHeaders headers) {
        MailProcessingContext context = context(headers);
        if (context == null || context.decision().quarantine() == null) {
            return null;
        }
        return context.decision().quarantine().detail();
    }
}
