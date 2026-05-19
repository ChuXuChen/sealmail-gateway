package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailFlowRouteDecider {

    private final MailProcessingTracker tracker;

    public MailFlowRouteDecider(MailProcessingTracker tracker) {
        this.tracker = tracker;
    }

    public MailFlowRoute deliveryRoute(Message<?> message) {
        MailProcessingContext context = MailProcessingMessages.context(message);
        if (context != null && context.decision().requiresQuarantine()) {
            tracker.completeProcessing(context.processingId(), ProcessingResult.FAILED);
            return MailFlowRoute.QUARANTINE;
        }
        return MailFlowRoute.RELAY;
    }

    public MailFlowRoute releaseDirectionRoute(Message<?> message) {
        MailProcessingContext context = MailProcessingMessages.context(message);
        if (context != null && context.direction() == MailDirection.INBOUND) {
            return MailFlowRoute.RELEASE_INBOUND;
        }
        return MailFlowRoute.RELEASE_OUTBOUND;
    }

    public MailFlowRoute inboundReleaseRoute(Message<?> message) {
        MailProcessingContext context = MailProcessingMessages.context(message);
        if (context != null
                && (context.decision().encryptionRequired() || context.decision().mustEncrypt())) {
            return MailFlowRoute.ENCRYPT_THEN_RELAY;
        }
        return MailFlowRoute.RELAY;
    }
}
