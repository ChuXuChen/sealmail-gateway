package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.infra.mail.pipeline.step.*;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
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
                .handle((payload, headers) -> routingService.routeInbound(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build()))
                .handle((payload, headers) -> quarantineIfRequired(payload, headers, quarantineChannel))
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build(),
                        mailAuthenticationStep))
                .<PipelineResult, Boolean>route(result -> result.success(),
                        authMapping -> authMapping
                                .subFlowMapping(true, authSf -> authSf
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                messageWithOptionalHeaders((PipelineResult) payload, headers, true),
                                                decryptStep))
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                org.springframework.messaging.support.MessageBuilder.withPayload(((PipelineResult) payload).payload())
                                                        .copyHeaders(headers).build(),
                                                verifyStep))
                                        .<PipelineResult, Boolean>route(result -> result.success(),
                                                mapping -> mapping
                                                        .subFlowMapping(true, sf -> sf
                                                                .handle((p, h) -> stepTracker.executeWithTracking(
                                                                        messageWithOptionalHeaders((PipelineResult) p, h, true),
                                                                        dlpStep))
                                                                .<PipelineResult, Boolean>route(result -> result.success(),
                                                                        innerMapping -> innerMapping
                                                                                .subFlowMapping(true, innerSf -> innerSf
                                                                                        .<PipelineResult, byte[]>transform(PipelineResult::payload)
                                                                                        .channel(relayChannel))
                                                                                .subFlowMapping(false, innerSf -> innerSf
                                                                                        .handle((p, h) -> {
                                                                                            String processingId = (String) h.get("processingId");
                                                                                            stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                                                                            return p;
                                                                                        })
                                                                                        .channel(quarantineChannel))))
                                                        .subFlowMapping(false, sf -> sf
                                                                .handle((p, h) -> {
                                                                    String processingId = (String) h.get("processingId");
                                                                    stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                                                    return p;
                                                                })
                                                                .channel(quarantineChannel))))
                                .subFlowMapping(false, authSf -> authSf
                                        .handle((p, h) -> {
                                            String processingId = (String) h.get("processingId");
                                            stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                            return p;
                                        })
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow outboundFlow(MessageChannel mailOutboundChannel,
                                         MessageChannel quarantineChannel,
                                         MessageChannel relayChannel) {
        return IntegrationFlow.from(mailOutboundChannel)
                .handle((payload, headers) -> routingService.routeOutbound(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build()))
                .handle((payload, headers) -> quarantineIfRequired(payload, headers, quarantineChannel))
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build(),
                        dlpStep))
                .<PipelineResult, Boolean>route(result -> result.success(),
                        dlpMapping -> dlpMapping
                                .subFlowMapping(true, dlpSf -> dlpSf
                                        .handle((payload, headers) -> stepTracker.executeWithTracking(
                                                messageWithOptionalHeaders((PipelineResult) payload, headers, true),
                                                signStep))
                                        .<PipelineResult, Boolean>route(result -> result.success(),
                                                mapping -> mapping
                                                        .subFlowMapping(true, sf -> sf
                                                                .handle((p, h) -> stepTracker.executeWithTracking(
                                                                        messageWithOptionalHeaders((PipelineResult) p, h, true),
                                                                        encryptStep))
                                                                .<PipelineResult, Boolean>route(result -> result.success(),
                                                                        innerMapping -> innerMapping
                                                                                .subFlowMapping(true, innerSf -> innerSf
                                                                                        .<PipelineResult, byte[]>transform(PipelineResult::payload)
                                                                                        .handle((p, h) -> stepTracker.executeWithTracking(
                                                                                                org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) p)
                                                                                                        .copyHeaders(h).build(),
                                                                                                dkimSignStep))
                                                                                        .<PipelineResult, byte[]>transform(PipelineResult::payload)
                                                                                        .channel(relayChannel))
                                                                                .subFlowMapping(false, innerSf -> innerSf
                                                                                        .handle((p, h) -> {
                                                                                            String processingId = (String) h.get("processingId");
                                                                                            stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                                                                            return p;
                                                                                        })
                                                                                        .channel(quarantineChannel))))
                                                        .subFlowMapping(false, sf -> sf
                                                                .handle((p, h) -> {
                                                                    String processingId = (String) h.get("processingId");
                                                                    stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                                                    return p;
                                                                })
                                                                .channel(quarantineChannel))))
                                .subFlowMapping(false, dlpSf -> dlpSf
                                        .handle((p, h) -> {
                                            String processingId = (String) h.get("processingId");
                                            stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                            return p;
                                        })
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow quarantineFlow(MessageChannel quarantineChannel) {
        return IntegrationFlow.from(quarantineChannel)
                .handle((payload, headers) -> {
                    if (payload instanceof PipelineResult result) {
                        return stepTracker.executeWithTracking(
                                org.springframework.messaging.support.MessageBuilder.withPayload(quarantinePayload(result, headers))
                                        .copyHeaders(headers)
                                        .setHeader("quarantineReason", quarantineReason(result, headers))
                                        .setHeader("quarantineDetail", quarantineDetail(result, headers))
                                        .setHeader("mailRecordDisposition", recordDisposition(result, headers).name())
                                        .build(),
                                quarantineStep);
                    }
                    return stepTracker.executeWithTracking(
                            org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                    .copyHeaders(headers).build(),
                            quarantineStep);
                })
                .nullChannel();
    }

    private byte[] quarantinePayload(PipelineResult result,
                                     org.springframework.messaging.MessageHeaders headers) {
        if (result.payload() != null && result.payload().length > 0) {
            return result.payload();
        }
        Object original = headers.get("originalMailContent");
        if (original instanceof byte[] bytes) {
            return bytes;
        }
        return new byte[0];
    }

    private String quarantineReason(PipelineResult result,
                                    org.springframework.messaging.MessageHeaders headers) {
        if (result.quarantineReason() != null && !result.quarantineReason().isBlank()) {
            return result.quarantineReason();
        }
        Object headerReason = headers.get("quarantineReason");
        return headerReason instanceof String reason && !reason.isBlank()
                ? reason
                : "POLICY_VIOLATION";
    }

    private String quarantineDetail(PipelineResult result,
                                    org.springframework.messaging.MessageHeaders headers) {
        if (result.quarantineDetail() != null && !result.quarantineDetail().isBlank()) {
            return result.quarantineDetail();
        }
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        Object headerDetail = headers.get("quarantineDetail");
        return headerDetail instanceof String detail && !detail.isBlank()
                ? detail
                : quarantineReason(result, headers);
    }

    private MailRecordDisposition recordDisposition(PipelineResult result,
                                                    org.springframework.messaging.MessageHeaders headers) {
        if (result.recordDisposition() != null) {
            return result.recordDisposition();
        }
        Object headerDisposition = headers.get("mailRecordDisposition");
        if (headerDisposition instanceof MailRecordDisposition disposition) {
            return disposition;
        }
        if (headerDisposition instanceof String stringValue && !stringValue.isBlank()) {
            return MailRecordDisposition.valueOf(stringValue);
        }
        return MailRecordDisposition.EXCEPTION;
    }

    private org.springframework.messaging.Message<byte[]> quarantineIfRequired(
            Object payload,
            org.springframework.messaging.MessageHeaders headers,
            MessageChannel quarantineChannel) {
        byte[] mailPayload = payload instanceof byte[] bytes ? bytes : new byte[0];
        if (!Boolean.TRUE.equals(headers.get("quarantineRequired"))) {
            return org.springframework.messaging.support.MessageBuilder.withPayload(mailPayload)
                    .copyHeaders(headers)
                    .build();
        }

        String processingId = (String) headers.get("processingId");
        stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
        quarantineChannel.send(org.springframework.messaging.support.MessageBuilder
                .withPayload(mailPayload)
                .copyHeaders(headers)
                .build());
        return null;
    }

    private org.springframework.messaging.Message<byte[]> messageWithOptionalHeaders(PipelineResult result,
                                                                                    org.springframework.messaging.MessageHeaders headers,
                                                                                    boolean preservePipelineHeaders) {
        org.springframework.messaging.support.MessageBuilder<byte[]> builder =
                org.springframework.messaging.support.MessageBuilder.withPayload(result.payload())
                        .copyHeaders(headers);
        if (result.headers() != null && !result.headers().isEmpty()) {
            builder.copyHeaders(result.headers());
        }
        if ((result.headers() == null || result.headers().isEmpty())
                && result.headerName() != null && result.headerValue() != null) {
            builder.setHeader(result.headerName(), result.headerValue());
        }
        if (preservePipelineHeaders && headers.containsKey("mustEncrypt")) {
            builder.setHeaderIfAbsent("mustEncrypt", headers.get("mustEncrypt"));
        }
        if (preservePipelineHeaders && headers.containsKey("preferredAlgorithm")) {
            builder.setHeaderIfAbsent("preferredAlgorithm", headers.get("preferredAlgorithm"));
        }
        return builder.build();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel, MessageChannel quarantineChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build(),
                        relayStep))
                .<PipelineResult, Boolean>route(PipelineResult::success,
                        mapping -> mapping
                                .subFlowMapping(true, sf -> sf
                                        .handle((p, h) -> {
                                            String processingId = (String) h.get("processingId");
                                            stepTracker.completeProcessing(processingId, ProcessingResult.SUCCESS);
                                            return p;
                                        })
                                        .nullChannel())
                                .subFlowMapping(false, sf -> sf
                                        .handle((p, h) -> {
                                            String processingId = (String) h.get("processingId");
                                            stepTracker.completeProcessing(processingId, ProcessingResult.FAILED);
                                            return p;
                                        })
                                        .channel(quarantineChannel)))
                .get();
    }

    public IntegrationFlow relayFlow(MessageChannel relayChannel) {
        return nullQuarantineRelayFlow(relayChannel);
    }

    private IntegrationFlow nullQuarantineRelayFlow(MessageChannel relayChannel) {
        return IntegrationFlow.from(relayChannel)
                .handle((payload, headers) -> stepTracker.executeWithTracking(
                        org.springframework.messaging.support.MessageBuilder.withPayload((byte[]) payload)
                                .copyHeaders(headers).build(),
                        relayStep))
                .nullChannel();
    }
}
