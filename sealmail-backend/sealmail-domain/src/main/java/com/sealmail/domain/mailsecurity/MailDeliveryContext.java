package com.sealmail.domain.mailsecurity;

public record MailDeliveryContext(
        MailDirection direction,
        RoutingDecision routingDecision,
        RelayProfile relayProfile,
        MailRecordDisposition recordDisposition,
        String quarantineReleaseId
) {
}
