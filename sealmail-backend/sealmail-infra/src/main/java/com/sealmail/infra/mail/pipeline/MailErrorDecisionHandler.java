package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.MailProcessingDecision;
import com.sealmail.domain.mailsecurity.MailProcessingRepository;
import com.sealmail.infra.events.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class MailErrorDecisionHandler {

    private static final Logger log = LoggerFactory.getLogger(MailErrorDecisionHandler.class);

    private final MailProcessingRepository mailProcessingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final MailErrorClassifier classifier;

    public MailErrorDecisionHandler(MailProcessingRepository mailProcessingRepository,
                                    DomainEventPublisher domainEventPublisher) {
        this(mailProcessingRepository, domainEventPublisher, new MailErrorClassifier());
    }

    @Autowired
    public MailErrorDecisionHandler(MailProcessingRepository mailProcessingRepository,
                                    DomainEventPublisher domainEventPublisher,
                                    MailErrorClassifier classifier) {
        this.mailProcessingRepository = mailProcessingRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.classifier = classifier;
    }

    public ErrorDecision decide(Message<?> errorMessage) {
        MailErrorClassification classification = classifier.classify(errorMessage);
        updateProcessing(classification);
        recordAudit(classification);

        if (classification.target() == MailErrorTarget.QUARANTINE) {
            return ErrorDecision.quarantine(quarantineMessage(classification));
        }
        return ErrorDecision.deadLetter();
    }

    private void updateProcessing(MailErrorClassification classification) {
        MailProcessingContext context = classification.context();
        if (context == null || context.processingId() == null) {
            return;
        }
        try {
            mailProcessingRepository.findById(context.processingId()).ifPresent(processing -> {
                processing.completeProcessing(classification.processingResult());
                mailProcessingRepository.save(processing);
            });
        } catch (Exception e) {
            log.warn("Failed to update mail processing error state for {}: {}",
                    context.processingId(), e.getMessage());
        }
    }

    private void recordAudit(MailErrorClassification classification) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_QUARANTINED,
                classification.context(),
                classification.auditAction(),
                "errorType=" + classification.errorType().name()
                        + ", retryable=" + classification.retryable()
                        + ", target=" + classification.target()
                        + ", processingResult=" + classification.processingResult()
                        + ", recordDisposition=" + classification.recordDisposition()
                        + MailProcessingAuditEvents.detailPresence(classification.detail()),
                false);
    }

    private Message<byte[]> quarantineMessage(MailErrorClassification classification) {
        MailProcessingContext context = classification.context();
        MailProcessingContext quarantineContext = context
                .withDecision(MailProcessingDecision.none().withQuarantine(
                        classification.quarantineReason(),
                        classification.quarantineDetail()))
                .withRecordDisposition(classification.recordDisposition());
        return MessageBuilder.withPayload(context.originalMailContent())
                .setHeader(MailProcessingHeaders.CONTEXT, quarantineContext)
                .build();
    }

    public record ErrorDecision(boolean quarantine, Message<byte[]> quarantineMessage) {
        public static ErrorDecision quarantine(Message<byte[]> message) {
            return new ErrorDecision(true, message);
        }

        public static ErrorDecision deadLetter() {
            return new ErrorDecision(false, null);
        }
    }
}
