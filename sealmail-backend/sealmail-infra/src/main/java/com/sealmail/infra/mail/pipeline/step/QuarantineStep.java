package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineNotificationPort;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.mail.pipeline.MailProcessingAuditEvents;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Pipeline step: Persist mail to quarantine storage.
 */
@Component
public class QuarantineStep {

    private static final Logger log = LoggerFactory.getLogger(QuarantineStep.class);

    private final QuarantineRepository quarantineRepository;
    private final ExceptionMailRepository exceptionMailRepository;
    private final QuarantinePolicyPort quarantinePolicyPort;
    private final QuarantineNotificationPort quarantineNotificationPort;
    private final DomainEventPublisher domainEventPublisher;

    public QuarantineStep(QuarantineRepository quarantineRepository,
                          ExceptionMailRepository exceptionMailRepository,
                          QuarantinePolicyPort quarantinePolicyPort,
                          QuarantineNotificationPort quarantineNotificationPort) {
        this(quarantineRepository, exceptionMailRepository, quarantinePolicyPort,
                quarantineNotificationPort, null);
    }

    @Autowired
    public QuarantineStep(QuarantineRepository quarantineRepository,
                          ExceptionMailRepository exceptionMailRepository,
                          QuarantinePolicyPort quarantinePolicyPort,
                          QuarantineNotificationPort quarantineNotificationPort,
                          DomainEventPublisher domainEventPublisher) {
        this.quarantineRepository = quarantineRepository;
        this.exceptionMailRepository = exceptionMailRepository;
        this.quarantinePolicyPort = quarantinePolicyPort;
        this.quarantineNotificationPort = quarantineNotificationPort;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> execute(Message<byte[]> message) {
        MailProcessingContext context = context(message);
        String reason = quarantineReason(context);
        if (reason == null) {
            reason = "POLICY_VIOLATION";
        }
        QuarantineReason quarantineReason = parseReason(reason);
        MailEnvelope envelope = context != null ? context.envelope() : null;
        MailRecordDisposition disposition = context != null && context.recordDisposition() != null
                ? context.recordDisposition()
                : MailRecordDisposition.EXCEPTION;

        try {
            if (disposition == MailRecordDisposition.DLP_QUARANTINE) {
                QuarantinedMail quarantined = QuarantinedMail.create(
                        java.util.UUID.randomUUID().toString(),
                        messageId(context),
                        subject(context),
                        sender(envelope),
                        recipients(envelope),
                        direction(context),
                        remoteAddress(context),
                        quarantineReason,
                        detail(message, reason),
                        message.getPayload()
                );
                quarantineRepository.save(quarantined);
                notifyIfEnabled(quarantined);
                recordQuarantineAudit(context, quarantined.getId(), disposition, quarantineReason, detail(message, reason));
            } else {
                ExceptionMail exceptionMail = ExceptionMail.create(
                        java.util.UUID.randomUUID().toString(),
                        messageId(context),
                        subject(context),
                        sender(envelope),
                        recipients(envelope),
                        direction(context),
                        remoteAddress(context),
                        quarantineReason,
                        detail(message, reason),
                        blockComment(detail(message, reason)),
                        message.getPayload()
                );
                exceptionMailRepository.save(exceptionMail);
                recordQuarantineAudit(context, exceptionMail.getId(), disposition, quarantineReason, detail(message, reason));
            }

            return message;

        } catch (Exception e) {
            throw new MailProcessingException(
                    MailProcessingErrorType.QUARANTINE,
                    "Failed to quarantine mail: " + e.getMessage(),
                    context,
                    e);
        }
    }

    private void notifyIfEnabled(QuarantinedMail quarantined) {
        try {
            if (!quarantinePolicyPort.getSettings().notificationEnabled()) {
                return;
            }
            quarantineNotificationPort.notifyCreated(quarantined);
        } catch (Exception e) {
            log.warn("Failed to send quarantine notification for {}: {}",
                    quarantined.getId(), e.getMessage());
        }
    }

    private void recordQuarantineAudit(MailProcessingContext context,
                                       String quarantineId,
                                       MailRecordDisposition disposition,
                                       QuarantineReason reason,
                                       String detail) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_QUARANTINED,
                context,
                disposition == MailRecordDisposition.DLP_QUARANTINE
                        ? "DLP_QUARANTINE_PERSISTED"
                        : "EXCEPTION_MAIL_PERSISTED",
                "quarantineId=" + quarantineId
                        + ", disposition=" + disposition
                        + ", reason=" + reason
                        + ", detailCategory=" + detailCategory(detail)
                        + MailProcessingAuditEvents.detailPresence(detail),
                false);
    }

    private String detailCategory(String detail) {
        if (detail == null || detail.isBlank()) {
            return "NONE";
        }
        if (detail.startsWith("DLP BLOCK")) {
            return "DLP_BLOCK";
        }
        if (detail.startsWith("DLP QUARANTINE")) {
            return "DLP_QUARANTINE";
        }
        return "PROCESSING_ERROR";
    }

    public String getStepName() {
        return "quarantine";
    }

    private QuarantineReason parseReason(String reason) {
        try {
            return QuarantineReason.valueOf(reason);
        } catch (Exception e) {
            return QuarantineReason.POLICY_VIOLATION;
        }
    }

    private String detail(Message<byte[]> message, String fallback) {
        String detail = quarantineDetail(context(message));
        return detail != null ? detail : fallback;
    }

    private String blockComment(String detail) {
        if (detail != null && detail.startsWith("DLP BLOCK")) {
            return "DLP policy blocked";
        }
        return "Exception mail blocked automatically";
    }

    private String messageId(MailProcessingContext context) {
        return context != null ? context.envelope().getMessageId() : null;
    }

    private String subject(MailProcessingContext context) {
        return context != null ? context.subject() : null;
    }

    private com.sealmail.domain.shared.model.EmailAddress sender(MailEnvelope envelope) {
        return envelope != null
                ? envelope.getSender()
                : new com.sealmail.domain.shared.model.EmailAddress("unknown@invalid.local");
    }

    private java.util.List<com.sealmail.domain.shared.model.EmailAddress> recipients(MailEnvelope envelope) {
        return envelope != null
                ? envelope.getRecipients()
                : java.util.List.of(new com.sealmail.domain.shared.model.EmailAddress("unknown@invalid.local"));
    }

    private MailDirection direction(MailProcessingContext context) {
        return context != null ? context.direction() : null;
    }

    private String remoteAddress(MailProcessingContext context) {
        if (context == null) {
            return null;
        }
        if (context.auditTrace() != null && context.auditTrace().remoteAddress() != null) {
            return context.auditTrace().remoteAddress();
        }
        return context.envelope().getRemoteHost();
    }

    private MailProcessingContext context(Message<?> message) {
        Object value = message.getHeaders().get(MailProcessingHeaders.CONTEXT);
        return value instanceof MailProcessingContext context ? context : null;
    }

    private String quarantineReason(MailProcessingContext context) {
        if (context == null || context.decision().quarantine() == null) {
            return null;
        }
        return context.decision().quarantine().reason();
    }

    private String quarantineDetail(MailProcessingContext context) {
        if (context == null || context.decision().quarantine() == null) {
            return null;
        }
        return context.decision().quarantine().detail();
    }
}
