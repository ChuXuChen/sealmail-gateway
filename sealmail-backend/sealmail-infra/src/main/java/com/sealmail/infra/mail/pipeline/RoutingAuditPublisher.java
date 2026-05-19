package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.infra.events.DomainEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class RoutingAuditPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public RoutingAuditPublisher(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    public void publishRouting(MailProcessingContext context, RoutingDecision decision) {
        String action = decision instanceof RoutingDecision.Quarantine ? "MAIL_ROUTE_QUARANTINE" : "MAIL_ROUTE";
        boolean success = !(decision instanceof RoutingDecision.Quarantine);
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_ROUTED,
                context,
                action,
                MailProcessingAuditEvents.routeDecisionSummary(decision),
                success);
    }

    public void publishCertificateSelection(MailProcessingContext context) {
        MailProcessingAuditEvents.publish(
                domainEventPublisher,
                AuditLogType.EMAIL_CERTIFICATE_SELECTED,
                context,
                "MAIL_CERTIFICATE_SELECTION",
                MailProcessingAuditEvents.certificateSelectionSummary(context.certificateSelection()));
    }
}
