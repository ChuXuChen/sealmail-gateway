package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import com.sealmail.infra.events.DomainEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailFlowCompletionService {

    private final MailProcessingTracker tracker;
    private final DomainEventPublisher domainEventPublisher;

    public MailFlowCompletionService(MailProcessingTracker tracker,
                                     DomainEventPublisher domainEventPublisher) {
        this.tracker = tracker;
        this.domainEventPublisher = domainEventPublisher;
    }

    public Message<byte[]> completeSuccess(Object message) {
        Message<byte[]> typedMessage = MailProcessingMessages.asByteMessage(message);
        tracker.completeProcessing(MailProcessingMessages.processingId(typedMessage), ProcessingResult.SUCCESS);
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_DELIVERED,
                MailProcessingMessages.context(typedMessage),
                "MAIL_PROCESSING_COMPLETED",
                "result=SUCCESS");
        return typedMessage;
    }
}
