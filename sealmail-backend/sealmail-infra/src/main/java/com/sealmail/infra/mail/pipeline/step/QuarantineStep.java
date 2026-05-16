package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingErrorType;
import com.sealmail.domain.mailsecurity.MailProcessingException;
import com.sealmail.domain.mailsecurity.MailRecordDisposition;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.infra.mail.pipeline.MailProcessingHeaders;
import com.sealmail.infra.persistence.repository.QuarantineRepositoryImpl;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Pipeline step: Persist mail to quarantine storage.
 */
@Component
public class QuarantineStep {

    private final QuarantineRepositoryImpl quarantineRepository;
    private final ExceptionMailRepository exceptionMailRepository;

    public QuarantineStep(QuarantineRepositoryImpl quarantineRepository,
                          ExceptionMailRepository exceptionMailRepository) {
        this.quarantineRepository = quarantineRepository;
        this.exceptionMailRepository = exceptionMailRepository;
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
