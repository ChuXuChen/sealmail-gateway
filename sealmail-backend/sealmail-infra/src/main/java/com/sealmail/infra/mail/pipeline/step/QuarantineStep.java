package com.sealmail.infra.mail.pipeline.step;

import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.exceptionmail.ExceptionMailRepository;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.infra.mail.pipeline.MailPipelineStep;
import com.sealmail.infra.mail.pipeline.MailRecordDisposition;
import com.sealmail.infra.mail.pipeline.PipelineResult;
import com.sealmail.infra.persistence.repository.QuarantineRepositoryImpl;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Pipeline step: Persist mail to quarantine storage.
 */
@Component
public class QuarantineStep implements MailPipelineStep {

    private final QuarantineRepositoryImpl quarantineRepository;
    private final ExceptionMailRepository exceptionMailRepository;

    public QuarantineStep(QuarantineRepositoryImpl quarantineRepository,
                          ExceptionMailRepository exceptionMailRepository) {
        this.quarantineRepository = quarantineRepository;
        this.exceptionMailRepository = exceptionMailRepository;
    }

    @Override
    public PipelineResult execute(Message<byte[]> message) {
        String reason = (String) message.getHeaders().get("quarantineReason");
        if (reason == null) {
            reason = "POLICY_VIOLATION";
        }
        QuarantineReason quarantineReason = parseReason(reason);
        MailEnvelope envelope = (MailEnvelope) message.getHeaders().get("mailEnvelope");
        MailRecordDisposition disposition = recordDisposition(message);

        try {
            if (disposition == MailRecordDisposition.DLP_QUARANTINE) {
                QuarantinedMail quarantined = QuarantinedMail.create(
                        java.util.UUID.randomUUID().toString(),
                        stringHeader(message, "messageId", envelope != null ? envelope.getMessageId() : null),
                        (String) message.getHeaders().get("subject"),
                        emailHeader(message, "sender", envelope),
                        recipientsHeader(message, envelope),
                        directionHeader(message),
                        stringHeader(message, "remoteAddress", envelope != null ? envelope.getRemoteHost() : null),
                        quarantineReason,
                        detail(message, reason),
                        message.getPayload()
                );
                quarantineRepository.save(quarantined);
            } else {
                ExceptionMail exceptionMail = ExceptionMail.create(
                        java.util.UUID.randomUUID().toString(),
                        stringHeader(message, "messageId", envelope != null ? envelope.getMessageId() : null),
                        (String) message.getHeaders().get("subject"),
                        emailHeader(message, "sender", envelope),
                        recipientsHeader(message, envelope),
                        directionHeader(message),
                        stringHeader(message, "remoteAddress", envelope != null ? envelope.getRemoteHost() : null),
                        quarantineReason,
                        detail(message, reason),
                        blockComment(detail(message, reason))
                );
                exceptionMailRepository.save(exceptionMail);
            }

            return PipelineResult.success(message.getPayload());

        } catch (Exception e) {
            return PipelineResult.failure("Failed to quarantine mail: " + e.getMessage());
        }
    }

    @Override
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
        String detail = (String) message.getHeaders().get("quarantineDetail");
        return detail != null ? detail : fallback;
    }

    private MailRecordDisposition recordDisposition(Message<byte[]> message) {
        Object value = message.getHeaders().get("mailRecordDisposition");
        if (value instanceof MailRecordDisposition disposition) {
            return disposition;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return MailRecordDisposition.valueOf(stringValue);
            } catch (IllegalArgumentException ignored) {
                return MailRecordDisposition.EXCEPTION;
            }
        }
        return MailRecordDisposition.EXCEPTION;
    }

    private String blockComment(String detail) {
        if (detail != null && detail.startsWith("DLP BLOCK")) {
            return "DLP policy blocked";
        }
        return "Exception mail blocked automatically";
    }

    private String stringHeader(Message<byte[]> message, String name, String fallback) {
        Object value = message.getHeaders().get(name);
        return value instanceof String stringValue ? stringValue : fallback;
    }

    private com.sealmail.domain.shared.model.EmailAddress emailHeader(Message<byte[]> message, String name, MailEnvelope envelope) {
        Object value = message.getHeaders().get(name);
        if (value instanceof com.sealmail.domain.shared.model.EmailAddress email) {
            return email;
        }
        return envelope != null ? envelope.getSender() : new com.sealmail.domain.shared.model.EmailAddress("unknown@invalid.local");
    }

    private MailDirection directionHeader(Message<byte[]> message) {
        Object value = message.getHeaders().get("mailDirection");
        if (value instanceof MailDirection direction) {
            return direction;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return MailDirection.valueOf(stringValue);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<com.sealmail.domain.shared.model.EmailAddress> recipientsHeader(Message<byte[]> message, MailEnvelope envelope) {
        Object value = message.getHeaders().get("recipients");
        if (value instanceof java.util.List<?> list && (list.isEmpty()
                || list.get(0) instanceof com.sealmail.domain.shared.model.EmailAddress)) {
            return (java.util.List<com.sealmail.domain.shared.model.EmailAddress>) list;
        }
        return envelope != null ? envelope.getRecipients() : java.util.List.of(new com.sealmail.domain.shared.model.EmailAddress("unknown@invalid.local"));
    }
}
