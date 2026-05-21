package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.ProcessingResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailFlowRouteDecider {

    private final MailProcessingTracker tracker;
    private final UnifiedMailDecisionService decisionService;

    public MailFlowRouteDecider(MailProcessingTracker tracker,
                                UnifiedMailDecisionService decisionService) {
        this.tracker = tracker;
        this.decisionService = decisionService;
    }

    public MailFlowRoute deliveryRoute(Message<?> message) {
        MailProcessingContext context = MailProcessingMessages.context(message);
        if (decisionService.requiresQuarantine(context)) {
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
