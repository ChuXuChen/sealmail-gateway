package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailProcessingContext;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MailRoutingStep {

    private final RoutingService routingService;

    public MailRoutingStep(RoutingService routingService) {
        this.routingService = routingService;
    }

    public Message<byte[]> routeInbound(Message<byte[]> message) {
        return routingService.routeInbound(message);
    }

    public Message<byte[]> routeOutbound(Message<byte[]> message) {
        return routingService.routeOutbound(message);
    }

    public Message<byte[]> routeReleasedMail(Message<byte[]> message) {
        MailProcessingContext context = MailProcessingMessages.context(message);
        if (context != null && context.direction() == MailDirection.INBOUND) {
            return routingService.routeInbound(message);
        }
        return routingService.routeOutbound(message);
    }
}
