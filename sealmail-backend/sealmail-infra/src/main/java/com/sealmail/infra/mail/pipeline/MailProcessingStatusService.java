package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailauth.AuthenticationMechanismResult;
import com.sealmail.domain.mailauth.AuthenticationResult;
import com.sealmail.domain.mailauth.AuthenticationResultSet;
import com.sealmail.domain.mailauth.MailAuthDecision;
import com.sealmail.domain.dlp.DlpEvaluationResult;
import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityFinding;
import com.sealmail.domain.mailsecurity.AttachmentSecurityResult;
import com.sealmail.domain.mailsecurity.CertificateSelection;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.AuthMechanismStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.CertificateStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.AttachmentSecurityStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.DeliveryStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.DlpStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.FailureStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.FinalDispositionStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.MailAuthStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.OperationStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.RecipientCertificateStatus;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot.SmimeStatus;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.policy.DispositionAction;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

@Component
public class MailProcessingStatusService {

    private static final Logger log = LoggerFactory.getLogger(MailProcessingStatusService.class);
    private static final int MAX_DETAIL_LENGTH = 512;

    private final MailProcessingRepository repository;

    public MailProcessingStatusService(MailProcessingRepository repository) {
        this.repository = repository;
    }

    public void recordRouting(MailProcessingContext context) {
        update(context, current -> current
                .withCertificate(certificateStatus(context))
                .withSmime(smimeRequirements(context, current.smime()))
                .withDelivery(deliveryStatus(context, current.delivery())));
    }

    public void recordMailAuthentication(MailProcessingContext context, AuthenticationResultSet result) {
        update(context, current -> {
            MailAuthStatus status = mailAuthStatus(result);
            MailProcessingStatusSnapshot updated = current.withMailAuth(status);
            if (MailProcessingStatusSnapshot.FAIL.equals(status.status())) {
                updated = updated.withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.MAIL_AUTH.stepName(),
                        MailProcessingErrorType.AUTHENTICATION.name(),
                        status.reason(),
                        status.detail(),
                        false));
            }
            return updated;
        });
    }

    public void recordMailAuthenticationFailure(MailProcessingContext context, String detail) {
        update(context, current -> current
                .withMailAuth(new MailAuthStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        null,
                        null,
                        null,
                        null,
                        "MAIL_AUTH_EXCEPTION",
                        safe(detail)))
                .withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.MAIL_AUTH.stepName(),
                        MailProcessingErrorType.AUTHENTICATION.name(),
                        "MAIL_AUTH_EXCEPTION",
                        safe(detail),
                        false)));
    }

    public void recordSmimeSkipped(MailProcessingContext context, SmimeOperation operation, String reason) {
        update(context, current -> current.withSmime(withOperation(
                current.smime(),
                operation,
                OperationStatus.skipped(safe(reason)))));
    }

    public void recordSmimeSuccess(MailProcessingContext context,
                                   SmimeOperation operation,
                                   String algorithmSuite,
                                   String certificateThumbprint,
                                   List<String> recipientThumbprints,
                                   List<String> recipients) {
        update(context, current -> current.withSmime(withOperation(
                current.smime(),
                operation,
                new OperationStatus(
                        MailProcessingStatusSnapshot.PASS,
                        safe(algorithmSuite),
                        safe(certificateThumbprint),
                        safeList(recipientThumbprints),
                        safeList(recipients),
                        null))));
    }

    public void recordSmimeFailure(MailProcessingContext context,
                                   SmimeOperation operation,
                                   MailProcessingErrorType errorType,
                                   String detail) {
        update(context, current -> current
                .withSmime(withOperation(
                        current.smime(),
                        operation,
                        new OperationStatus(
                                MailProcessingStatusSnapshot.FAIL,
                                null,
                                null,
                                List.of(),
                                List.of(),
                                safe(detail))))
                .withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        operation.stepName(),
                        errorType != null ? errorType.name() : null,
                        errorType != null ? errorType.name() : null,
                        safe(detail),
                        false)));
    }

    public void recordDlpEvaluation(MailProcessingContext context,
                                    DlpEvaluationResult result,
                                    String summary) {
        update(context, current -> {
            DlpStatus status = dlpStatus(result, summary, null);
            MailProcessingStatusSnapshot updated = current.withDlp(status);
            if (result != null && result.hasMatches()
                    && (result.action() == DispositionAction.QUARANTINE
                    || result.action() == DispositionAction.BLOCK)) {
                updated = updated.withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.DLP.stepName(),
                        MailProcessingErrorType.DLP.name(),
                        result.action().name(),
                        safe(summary),
                        false));
            }
            return updated;
        });
    }

    public void recordDlpFailure(MailProcessingContext context, String detail) {
        update(context, current -> current
                .withDlp(dlpStatus(null, null, detail))
                .withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.DLP.stepName(),
                        MailProcessingErrorType.DLP.name(),
                        MailProcessingErrorType.DLP.name(),
                        safe(detail),
                        false)));
    }

    public void recordAttachmentSecurity(MailProcessingContext context,
                                         AttachmentSecurityResult result,
                                         String summary) {
        update(context, current -> {
            AttachmentSecurityStatus status = attachmentSecurityStatus(result, summary, null);
            MailProcessingStatusSnapshot updated = current.withAttachmentSecurity(status);
            if (result != null && result.hasFindings()
                    && (result.action() == AttachmentSecurityAction.QUARANTINE
                    || result.action() == AttachmentSecurityAction.BLOCK)) {
                updated = updated.withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.ATTACHMENT_SECURITY.stepName(),
                        MailProcessingErrorType.ATTACHMENT_SECURITY.name(),
                        result.action().name(),
                        safe(summary),
                        false));
            }
            return updated;
        });
    }

    public void recordAttachmentSecurityFailure(MailProcessingContext context, String detail) {
        update(context, current -> current
                .withAttachmentSecurity(attachmentSecurityStatus(null, null, detail))
                .withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.ATTACHMENT_SECURITY.stepName(),
                        MailProcessingErrorType.ATTACHMENT_SECURITY.name(),
                        MailProcessingErrorType.ATTACHMENT_SECURITY.name(),
                        safe(detail),
                        false)));
    }

    public void recordRelaySuccess(MailProcessingContext context) {
        update(context, current -> current
                .withDelivery(deliveryFromRelay(context, MailProcessingStatusSnapshot.DELIVERED, null))
                .withFinalDisposition(new FinalDispositionStatus(
                        MailProcessingStatusSnapshot.DELIVERED,
                        ProcessingResult.SUCCESS.name(),
                        "SMTP_RELAY",
                        null,
                        null))
                .withFailure(FailureStatus.pending()));
    }

    public void recordRelayFailure(MailProcessingContext context, String detail, boolean retryable) {
        update(context, current -> current
                .withDelivery(deliveryFromRelay(context, MailProcessingStatusSnapshot.EXCEPTION, safe(detail)))
                .withFinalDisposition(new FinalDispositionStatus(
                        MailProcessingStatusSnapshot.EXCEPTION,
                        ProcessingResult.EXCEPTION.name(),
                        "SMTP_RELAY_FAILED",
                        MailProcessingErrorType.RELAY.name(),
                        safe(detail)))
                .withFailure(new FailureStatus(
                        MailProcessingStatusSnapshot.FAIL,
                        MailFlowStep.RELAY.stepName(),
                        MailProcessingErrorType.RELAY.name(),
                        MailProcessingErrorType.RELAY.name(),
                        safe(detail),
                        retryable)));
    }

    public void recordQuarantine(MailProcessingContext context,
                                 String targetId,
                                 String targetType,
                                 String reason,
                                 String detail) {
        update(context, current -> current
                .withDelivery(new DeliveryStatus(
                        MailProcessingStatusSnapshot.QUARANTINED,
                        current.delivery().route(),
                        current.delivery().relayHost(),
                        current.delivery().relayPort(),
                        current.delivery().transportProfile(),
                        safe(targetId),
                        safe(targetType),
                        safe(reason),
                        safe(detail)))
                .withFinalDisposition(new FinalDispositionStatus(
                        MailProcessingStatusSnapshot.QUARANTINED,
                        ProcessingResult.FAILED.name(),
                        "QUARANTINE",
                        safe(reason),
                        safe(detail)))
                .withFailure(quarantineFailure(current, reason, detail)));
    }

    public void recordProcessingResult(String processingId,
                                       ProcessingResult result,
                                       MailProcessingContext context) {
        update(processingId, current -> current
                .withDelivery(deliveryFromResult(current.delivery(), result, context))
                .withFinalDisposition(finalDisposition(result, context, current)));
    }

    public void recordError(MailErrorClassification classification) {
        if (classification == null) {
            return;
        }
        update(classification.context(), current -> {
            String status = classification.target() == MailErrorTarget.QUARANTINE
                    ? MailProcessingStatusSnapshot.QUARANTINED
                    : MailProcessingStatusSnapshot.EXCEPTION;
            return current
                    .withDelivery(new DeliveryStatus(
                            status,
                            current.delivery().route(),
                            current.delivery().relayHost(),
                            current.delivery().relayPort(),
                            current.delivery().transportProfile(),
                            current.delivery().targetId(),
                            current.delivery().targetType(),
                            safe(classification.quarantineReason()),
                            safe(classification.quarantineDetail())))
                    .withFinalDisposition(new FinalDispositionStatus(
                            status,
                            classification.processingResult().name(),
                            classification.auditAction(),
                            classification.errorType().name(),
                            safe(classification.detail())))
                    .withFailure(new FailureStatus(
                            MailProcessingStatusSnapshot.FAIL,
                            stepName(classification.errorType()),
                            classification.errorType().name(),
                            safe(classification.quarantineReason()),
                            safe(classification.detail()),
                            classification.retryable()));
        });
    }

    private void update(MailProcessingContext context, UnaryOperator<MailProcessingStatusSnapshot> updater) {
        update(context != null ? context.processingId() : null, updater);
    }

    private void update(String processingId, UnaryOperator<MailProcessingStatusSnapshot> updater) {
        if (!hasText(processingId) || updater == null) {
            return;
        }
        try {
            repository.findById(processingId).ifPresent(processing -> update(processing, updater));
        } catch (Exception e) {
            log.warn("Failed to update mail processing status snapshot for {}: {}", processingId, e.getMessage());
        }
    }

    private void update(MailProcessing processing, UnaryOperator<MailProcessingStatusSnapshot> updater) {
        MailProcessingStatusSnapshot current = processing.getStatusSnapshot();
        MailProcessingStatusSnapshot updated = updater.apply(current != null ? current : MailProcessingStatusSnapshot.empty());
        processing.updateStatusSnapshot(updated);
        repository.save(processing);
    }

    private MailAuthStatus mailAuthStatus(AuthenticationResultSet result) {
        if (result == null) {
            return MailAuthStatus.pending();
        }
        AuthMechanismStatus spf = mechanism(result.spf());
        AuthMechanismStatus dkim = mechanism(result.bestDkim());
        AuthMechanismStatus dmarc = mechanism(result.dmarc());
        MailAuthDecision decision = result.decision();
        boolean evaluated = evaluated(result);
        boolean failed = (decision != null && decision.requiresQuarantine()) || failed(result.spf())
                || result.dkim().stream().anyMatch(this::failed)
                || failed(result.dmarc());
        String status = !evaluated
                ? MailProcessingStatusSnapshot.SKIPPED
                : failed ? MailProcessingStatusSnapshot.FAIL : MailProcessingStatusSnapshot.PASS;
        return new MailAuthStatus(
                status,
                spf,
                dkim,
                dmarc,
                decision != null && decision.action() != null ? decision.action().name() : null,
                decision != null ? safe(decision.reason()) : null,
                decision != null ? safe(decision.detail()) : null);
    }

    private boolean evaluated(AuthenticationResultSet result) {
        return result.spf().result() != AuthenticationResult.NONE
                || !result.dkim().isEmpty()
                || result.dmarc().result() != AuthenticationResult.NONE;
    }

    private boolean failed(AuthenticationMechanismResult result) {
        return result != null && result.result() != null && result.result().isFailure();
    }

    private AuthMechanismStatus mechanism(AuthenticationMechanismResult result) {
        if (result == null) {
            return null;
        }
        return new AuthMechanismStatus(
                result.result().name(),
                safe(result.domain()),
                safe(result.identity()),
                safe(result.detail()));
    }

    private DlpStatus dlpStatus(DlpEvaluationResult result, String summary, String failureReason) {
        if (failureReason != null) {
            return new DlpStatus(
                    MailProcessingStatusSnapshot.FAIL,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    safe(failureReason));
        }
        if (result == null) {
            return DlpStatus.pending();
        }
        return new DlpStatus(
                dlpSnapshotStatus(result),
                result.action().name(),
                result.recommendedAction().name(),
                result.maxSeverity(),
                result.matches().size(),
                result.matches().stream()
                        .map(match -> match.rule().name())
                        .distinct()
                        .limit(10)
                        .map(this::safe)
                        .filter(Objects::nonNull)
                        .toList(),
                safe(result.eventId()),
                result.monitorMode(),
                result.ubaRiskLevel().name(),
                result.ubaActionUpgraded(),
                safe(summary),
                null);
    }

    private AttachmentSecurityStatus attachmentSecurityStatus(AttachmentSecurityResult result,
                                                              String summary,
                                                              String failureReason) {
        if (failureReason != null) {
            return new AttachmentSecurityStatus(
                    MailProcessingStatusSnapshot.FAIL,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    safe(failureReason));
        }
        if (result == null) {
            return AttachmentSecurityStatus.pending();
        }
        List<AttachmentSecurityFinding> findings = result.findings().stream()
                .limit(10)
                .map(this::sanitizeFinding)
                .toList();
        List<String> warnings = safeList(result.warnings());
        if (result.findings().size() > findings.size()) {
            warnings = new java.util.ArrayList<>(warnings);
            warnings.add("Additional attachment findings omitted: " + (result.findings().size() - findings.size()));
            warnings = warnings.stream().distinct().toList();
        }
        return new AttachmentSecurityStatus(
                result.status(),
                result.action(),
                result.maxSeverity(),
                result.attachmentCount(),
                result.totalBytes(),
                findings,
                warnings,
                null);
    }

    private AttachmentSecurityFinding sanitizeFinding(AttachmentSecurityFinding finding) {
        if (finding == null) {
            return null;
        }
        return new AttachmentSecurityFinding(
                safe(finding.code()),
                finding.severity(),
                finding.action(),
                safe(finding.message()),
                safe(finding.fileName()),
                safe(finding.extension()),
                safe(finding.declaredMimeType()),
                safe(finding.detectedMimeType()),
                finding.archive(),
                finding.encrypted(),
                safeList(finding.nestedPath()));
    }

    private String dlpSnapshotStatus(DlpEvaluationResult result) {
        if (result == null) {
            return MailProcessingStatusSnapshot.PENDING;
        }
        if (!result.hasMatches()) {
            return MailProcessingStatusSnapshot.PASS;
        }
        if (result.action() == DispositionAction.QUARANTINE) {
            return MailProcessingStatusSnapshot.QUARANTINED;
        }
        if (result.action() == DispositionAction.BLOCK) {
            return MailProcessingStatusSnapshot.EXCEPTION;
        }
        return MailProcessingStatusSnapshot.PASS;
    }

    private CertificateStatus certificateStatus(MailProcessingContext context) {
        if (context == null) {
            return CertificateStatus.pending();
        }
        CertificateSelection selection = context.certificateSelection();
        boolean senderRequired = context.decision().signingRequired() || context.decision().verificationRequired();
        boolean recipientsRequired = context.decision().encryptionRequired()
                || context.decision().mustEncrypt()
                || context.decision().decryptionRequired();
        boolean senderMissing = senderRequired && !hasText(selection.senderCertificateThumbprint())
                && !hasText(selection.senderCertificatePem());
        List<RecipientCertificateStatus> recipients = recipientCertificateStatuses(context);
        List<String> missingRecipients = missingRecipients(context, recipientsRequired);
        boolean recipientMissing = recipientsRequired && !missingRecipients.isEmpty();
        String status = senderMissing || recipientMissing
                ? MailProcessingStatusSnapshot.FAIL
                : senderRequired || recipientsRequired || !recipients.isEmpty()
                    ? MailProcessingStatusSnapshot.PASS
                    : MailProcessingStatusSnapshot.SKIPPED;
        String failureReason = senderMissing
                ? "Missing sender certificate"
                : recipientMissing ? "Missing recipient certificate" : null;
        return new CertificateStatus(
                status,
                context.cryptoProfile() != null ? context.cryptoProfile().name() : null,
                safe(selection.senderCertificateThumbprint()),
                recipients,
                missingRecipients,
                senderMissing,
                recipientMissing,
                failureReason);
    }

    private List<RecipientCertificateStatus> recipientCertificateStatuses(MailProcessingContext context) {
        List<RecipientCertificateStatus> recipients = new ArrayList<>();
        CertificateSelection selection = context.certificateSelection();
        MailEnvelope envelope = context.envelope();
        for (EmailAddress recipient : envelope.getRecipients()) {
            String thumbprint = selection.recipientCertificateThumbprints().get(recipient);
            if (!hasText(thumbprint) && context.direction() == MailDirection.INBOUND) {
                thumbprint = selection.recipientCertificateThumbprint();
            }
            boolean selected = hasText(thumbprint)
                    || selection.recipientCertificates().containsKey(recipient)
                    || (context.direction() == MailDirection.INBOUND
                        && hasText(selection.recipientCertificatePem()));
            recipients.add(new RecipientCertificateStatus(
                    recipient.getValue(),
                    safe(thumbprint),
                    selected));
        }
        return recipients;
    }

    private List<String> missingRecipients(MailProcessingContext context, boolean recipientsRequired) {
        if (!recipientsRequired) {
            return List.of();
        }
        CertificateSelection selection = context.certificateSelection();
        if (context.direction() == MailDirection.INBOUND
                && (hasText(selection.recipientCertificateThumbprint())
                || hasText(selection.recipientCertificatePem()))) {
            return List.of();
        }
        return context.envelope().getRecipients().stream()
                .filter(recipient -> !hasText(selection.recipientCertificateThumbprints().get(recipient))
                        && !selection.recipientCertificates().containsKey(recipient))
                .map(EmailAddress::getValue)
                .toList();
    }

    private SmimeStatus smimeRequirements(MailProcessingContext context, SmimeStatus current) {
        if (context == null) {
            return current != null ? current : SmimeStatus.pending();
        }
        SmimeStatus base = current != null ? current : SmimeStatus.pending();
        return base
                .withSign(requirement(base.sign(), context.decision().signingRequired()))
                .withEncrypt(requirement(base.encrypt(), context.decision().requiresEncryption()))
                .withVerify(requirement(base.verify(), context.decision().verificationRequired()))
                .withDecrypt(requirement(base.decrypt(), context.decision().decryptionRequired()));
    }

    private OperationStatus requirement(OperationStatus current, boolean required) {
        if (current == null || MailProcessingStatusSnapshot.PENDING.equals(current.status())) {
            return required ? OperationStatus.pending() : OperationStatus.skipped("Not required by route");
        }
        return current;
    }

    private SmimeStatus withOperation(SmimeStatus current, SmimeOperation operation, OperationStatus value) {
        SmimeStatus base = current != null ? current : SmimeStatus.pending();
        return switch (operation) {
            case SIGN -> base.withSign(value);
            case ENCRYPT -> base.withEncrypt(value);
            case VERIFY -> base.withVerify(value);
            case DECRYPT -> base.withDecrypt(value);
        };
    }

    private DeliveryStatus deliveryStatus(MailProcessingContext context, DeliveryStatus current) {
        RelayProfile relayProfile = context != null ? context.relayProfile() : null;
        return new DeliveryStatus(
                current != null ? current.status() : MailProcessingStatusSnapshot.PENDING,
                route(context != null ? context.routingDecision() : null),
                safe(relayProfile != null ? relayProfile.host() : null),
                relayProfile != null ? relayProfile.port() : null,
                relayProfile != null && relayProfile.transportProfile() != null
                        ? relayProfile.transportProfile().name()
                        : null,
                current != null ? current.targetId() : null,
                current != null ? current.targetType() : null,
                current != null ? current.reason() : null,
                current != null ? current.detail() : null);
    }

    private DeliveryStatus deliveryFromRelay(MailProcessingContext context, String status, String detail) {
        RelayProfile relayProfile = context != null ? context.relayProfile() : null;
        return new DeliveryStatus(
                status,
                route(context != null ? context.routingDecision() : null),
                safe(relayProfile != null ? relayProfile.host() : null),
                relayProfile != null ? relayProfile.port() : null,
                relayProfile != null && relayProfile.transportProfile() != null
                        ? relayProfile.transportProfile().name()
                        : null,
                null,
                null,
                detail != null ? MailProcessingErrorType.RELAY.name() : null,
                safe(detail));
    }

    private DeliveryStatus deliveryFromResult(DeliveryStatus current,
                                              ProcessingResult result,
                                              MailProcessingContext context) {
        DeliveryStatus existing = current != null ? current : DeliveryStatus.pending();
        if (result == ProcessingResult.SUCCESS) {
            return new DeliveryStatus(
                    MailProcessingStatusSnapshot.DELIVERED,
                    existing.route(),
                    existing.relayHost(),
                    existing.relayPort(),
                    existing.transportProfile(),
                    existing.targetId(),
                    existing.targetType(),
                    existing.reason(),
                    existing.detail());
        }
        if (context != null && context.decision().requiresQuarantine()) {
            return new DeliveryStatus(
                    MailProcessingStatusSnapshot.QUARANTINED,
                    existing.route(),
                    existing.relayHost(),
                    existing.relayPort(),
                    existing.transportProfile(),
                    existing.targetId(),
                    existing.targetType(),
                    context.decision().quarantine().reason(),
                    safe(context.decision().quarantine().detail()));
        }
        if (result == ProcessingResult.EXCEPTION) {
            return new DeliveryStatus(
                    MailProcessingStatusSnapshot.EXCEPTION,
                    existing.route(),
                    existing.relayHost(),
                    existing.relayPort(),
                    existing.transportProfile(),
                    existing.targetId(),
                    existing.targetType(),
                    existing.reason(),
                    existing.detail());
        }
        return existing;
    }

    private FinalDispositionStatus finalDisposition(ProcessingResult result,
                                                    MailProcessingContext context,
                                                    MailProcessingStatusSnapshot current) {
        if (result == ProcessingResult.SUCCESS) {
            return new FinalDispositionStatus(
                    MailProcessingStatusSnapshot.DELIVERED,
                    result.name(),
                    "DELIVER",
                    null,
                    null);
        }
        if (context != null && context.decision().requiresQuarantine()) {
            return new FinalDispositionStatus(
                    MailProcessingStatusSnapshot.QUARANTINED,
                    result != null ? result.name() : null,
                    "QUARANTINE",
                    context.decision().quarantine().reason(),
                    safe(context.decision().quarantine().detail()));
        }
        if (result == ProcessingResult.EXCEPTION) {
            return new FinalDispositionStatus(
                    MailProcessingStatusSnapshot.EXCEPTION,
                    result.name(),
                    "EXCEPTION",
                    current.failure().reason(),
                    current.failure().detail());
        }
        if (result == ProcessingResult.FAILED) {
            String status = MailProcessingStatusSnapshot.QUARANTINED.equals(current.delivery().status())
                    ? MailProcessingStatusSnapshot.QUARANTINED
                    : MailProcessingStatusSnapshot.FAIL;
            return new FinalDispositionStatus(
                    status,
                    result.name(),
                    MailProcessingStatusSnapshot.QUARANTINED.equals(status) ? "QUARANTINE" : "FAILED",
                    current.failure().reason(),
                    current.failure().detail());
        }
        return current.finalDisposition();
    }

    private FailureStatus quarantineFailure(MailProcessingStatusSnapshot current, String reason, String detail) {
        FailureStatus existing = current != null ? current.failure() : null;
        if (existing != null && MailProcessingStatusSnapshot.FAIL.equals(existing.status())) {
            return existing;
        }
        return new FailureStatus(
                MailProcessingStatusSnapshot.FAIL,
                MailFlowStep.QUARANTINE.stepName(),
                null,
                safe(reason),
                safe(detail),
                false);
    }

    private String route(RoutingDecision decision) {
        if (decision == null) {
            return null;
        }
        if (decision instanceof RoutingDecision.OutboundEncrypt) {
            return "OUTBOUND_ENCRYPT";
        }
        if (decision instanceof RoutingDecision.OutboundSign) {
            return "OUTBOUND_SIGN";
        }
        if (decision instanceof RoutingDecision.InboundDecrypt) {
            return "INBOUND_DECRYPT";
        }
        if (decision instanceof RoutingDecision.InboundVerify) {
            return "INBOUND_VERIFY";
        }
        if (decision instanceof RoutingDecision.PassThrough) {
            return "PASS_THROUGH";
        }
        if (decision instanceof RoutingDecision.Quarantine quarantine) {
            return "QUARANTINE:" + quarantine.getReason().name();
        }
        return decision.getClass().getSimpleName();
    }

    private String stepName(MailProcessingErrorType errorType) {
        if (errorType == null) {
            return null;
        }
        return switch (errorType) {
            case AUTHENTICATION -> MailFlowStep.MAIL_AUTH.stepName();
            case DECRYPTION -> MailFlowStep.DECRYPT.stepName();
            case VERIFICATION -> MailFlowStep.VERIFY_SIGNATURE.stepName();
            case ATTACHMENT_SECURITY -> MailFlowStep.ATTACHMENT_SECURITY.stepName();
            case DLP -> MailFlowStep.DLP.stepName();
            case SIGNING -> MailFlowStep.SIGN.stepName();
            case ENCRYPTION -> MailFlowStep.ENCRYPT.stepName();
            case DKIM_SIGNING -> MailFlowStep.DKIM_SIGN.stepName();
            case RELAY -> MailFlowStep.RELAY.stepName();
            case QUARANTINE -> MailFlowStep.QUARANTINE.stepName();
            case ROUTING -> MailFlowStep.ROUTING.stepName();
            case PIPELINE, UNKNOWN -> null;
        };
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::safe)
                .filter(Objects::nonNull)
                .toList();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim()
                .replaceAll("-----BEGIN [^-]+-----.*?-----END [^-]+-----", "<redacted-pem>")
                .replaceAll("(?i)(privateKey|password|secret|token)\\s*[=:]\\s*([^,;\\s]+)", "$1=<redacted>");
        if (sanitized.length() <= MAX_DETAIL_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum SmimeOperation {
        SIGN(MailFlowStep.SIGN),
        ENCRYPT(MailFlowStep.ENCRYPT),
        VERIFY(MailFlowStep.VERIFY_SIGNATURE),
        DECRYPT(MailFlowStep.DECRYPT);

        private final MailFlowStep step;

        SmimeOperation(MailFlowStep step) {
            this.step = step;
        }

        public String stepName() {
            return step.stepName();
        }
    }
}
