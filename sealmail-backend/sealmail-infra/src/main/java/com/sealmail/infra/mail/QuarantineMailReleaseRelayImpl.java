package com.sealmail.infra.mail;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import com.sealmail.infra.mail.pipeline.PipelineStepTracker;
import com.sealmail.infra.mail.pipeline.RoutingService;
import com.sealmail.infra.mail.pipeline.step.DkimSignStep;
import com.sealmail.infra.mail.pipeline.step.EncryptStep;
import com.sealmail.infra.mail.pipeline.step.QuarantineStep;
import com.sealmail.infra.mail.pipeline.step.RelayStep;
import com.sealmail.infra.mail.pipeline.step.SignStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases DLP-quarantined mail by resuming the normal delivery path after DLP.
 */
@Component
public class QuarantineMailReleaseRelayImpl implements QuarantineMailReleaseRelay {

    private static final Logger log = LoggerFactory.getLogger(QuarantineMailReleaseRelayImpl.class);

    private final RoutingService routingService;
    private final PipelineStepTracker stepTracker;
    private final SignStep signStep;
    private final EncryptStep encryptStep;
    private final DkimSignStep dkimSignStep;
    private final RelayStep relayStep;
    private final QuarantineStep quarantineStep;

    public QuarantineMailReleaseRelayImpl(RoutingService routingService,
                                          PipelineStepTracker stepTracker,
                                          SignStep signStep,
                                          EncryptStep encryptStep,
                                          DkimSignStep dkimSignStep,
                                          RelayStep relayStep,
                                          QuarantineStep quarantineStep) {
        this.routingService = routingService;
        this.stepTracker = stepTracker;
        this.signStep = signStep;
        this.encryptStep = encryptStep;
        this.dkimSignStep = dkimSignStep;
        this.relayStep = relayStep;
        this.quarantineStep = quarantineStep;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void relay(QuarantinedMail mail, boolean encryptBeforeRelay) {
        Message<byte[]> routed = route(mail, encryptBeforeRelay);
        byte[] payload = routed.getPayload();

        if (direction(mail) == MailDirection.OUTBOUND) {
            Message<byte[]> stepMessage = messageWithPayload(routed, payload);
            payload = runStep(mail, stepMessage, stepTracker.executeWithTracking(stepMessage, signStep), "sign");

            stepMessage = messageWithPayload(routed, payload);
            payload = runStep(mail, stepMessage, stepTracker.executeWithTracking(stepMessage, encryptStep), "encrypt");

            stepMessage = messageWithPayload(routed, payload);
            payload = runStep(mail, stepMessage, stepTracker.executeWithTracking(stepMessage, dkimSignStep), "dkim-sign");
        } else if (encryptBeforeRelay) {
            Message<byte[]> stepMessage = messageWithPayload(routed, payload);
            payload = runStep(mail, stepMessage, stepTracker.executeWithTracking(stepMessage, encryptStep), "encrypt");
        }

        Message<byte[]> relayMessage = messageWithPayload(routed, payload);
        runStep(mail, relayMessage, stepTracker.executeWithTracking(relayMessage, relayStep), "relay");
        stepTracker.completeProcessing(stringHeader(routed, "processingId"), ProcessingResult.SUCCESS);
    }

    private Message<byte[]> route(QuarantinedMail mail, boolean encryptBeforeRelay) {
        Message<byte[]> message = MessageBuilder
                .withPayload(mail.getRawContent())
                .setHeader("mailEnvelope", envelope(mail))
                .setHeader("submissionType", "dlp_quarantine_release")
                .setHeader("mailDirection", direction(mail).name())
                .setHeader("remoteAddress", mail.getRemoteAddress())
                .build();

        Message<byte[]> routed = direction(mail) == MailDirection.OUTBOUND
                ? routingService.routeOutbound(message)
                : routingService.routeInbound(message);
        if (Boolean.TRUE.equals(routed.getHeaders().get("quarantineRequired"))) {
            String detail = stringHeader(routed, "quarantineDetail");
            recordRoutingFailure(routed, detail);
            throw new QuarantineReleaseRelayException(
                    mail.getId(),
                    detail != null ? detail : "released mail was routed back to quarantine");
        }

        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(routed.getPayload())
                .copyHeaders(routed.getHeaders())
                .setHeader("quarantineReleaseId", mail.getId());
        if (encryptBeforeRelay) {
            builder
                    .setHeader("encryptionEnabled", true)
                    .setHeader("mustEncrypt", "true");
        }
        return builder.build();
    }

    private byte[] runStep(QuarantinedMail mail,
                           Message<byte[]> stepMessage,
                           PipelineResult result,
                           String stepName) {
        if (result.success()) {
            return result.payload();
        }
        String detail = result.errorMessage() != null && !result.errorMessage().isBlank()
                ? result.errorMessage()
                : result.quarantineDetail();
        recordReleaseFailure(stepMessage, result, detail);
        throw new QuarantineReleaseRelayException(
                mail.getId(),
                stepName + " failed" + (detail != null && !detail.isBlank() ? ": " + detail : ""));
    }

    private void recordReleaseFailure(Message<byte[]> stepMessage,
                                      PipelineResult result,
                                      String detail) {
        try {
            Message<byte[]> quarantineMessage = MessageBuilder
                    .withPayload(stepMessage.getPayload())
                    .copyHeaders(stepMessage.getHeaders())
                    .setHeader("quarantineReason", hasText(result.quarantineReason())
                            ? result.quarantineReason()
                            : "POLICY_VIOLATION")
                    .setHeader("quarantineDetail", hasText(detail)
                            ? detail
                            : "released mail failed before relay")
                    .setHeader("mailRecordDisposition", "EXCEPTION")
                    .build();
            stepTracker.executeWithTracking(quarantineMessage, quarantineStep);
        } catch (Exception e) {
            log.error("Failed to record release failure as exception mail: {}", e.getMessage(), e);
        }
    }

    private void recordRoutingFailure(Message<byte[]> routed, String detail) {
        try {
            Message<byte[]> quarantineMessage = MessageBuilder
                    .withPayload(routed.getPayload())
                    .copyHeaders(routed.getHeaders())
                    .setHeader("quarantineDetail", hasText(detail)
                            ? detail
                            : "released mail was routed back to quarantine")
                    .setHeader("mailRecordDisposition", "EXCEPTION")
                    .build();
            stepTracker.executeWithTracking(quarantineMessage, quarantineStep);
        } catch (Exception e) {
            log.error("Failed to record release routing failure as exception mail: {}", e.getMessage(), e);
        }
    }

    private Message<byte[]> messageWithPayload(Message<byte[]> original, byte[] payload) {
        return MessageBuilder.withPayload(payload)
                .copyHeaders(original.getHeaders())
                .build();
    }

    private MailEnvelope envelope(QuarantinedMail mail) {
        return new MailEnvelope(
                messageId(mail),
                mail.getSender(),
                mail.getRecipients(),
                mail.getRemoteAddress(),
                "dlp-quarantine-release",
                mail.getCreatedAt(),
                mail.getRawContent()
        );
    }

    private String messageId(QuarantinedMail mail) {
        String value = mail.getMessageId();
        if (value != null && !value.isBlank()) {
            return value;
        }
        return mail.getId() + "@sealmail.local";
    }

    private MailDirection direction(QuarantinedMail mail) {
        if (mail.getDirection() != null) {
            return mail.getDirection();
        }
        log.warn("Quarantined mail {} has no direction; releasing as outbound for backward compatibility",
                mail.getId());
        return MailDirection.OUTBOUND;
    }

    private String stringHeader(Message<byte[]> message, String headerName) {
        Object value = message.getHeaders().get(headerName);
        return value instanceof String stringValue ? stringValue : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
