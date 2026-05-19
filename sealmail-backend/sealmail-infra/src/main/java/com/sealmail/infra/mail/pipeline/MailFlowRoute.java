package com.sealmail.infra.mail.pipeline;

public enum MailFlowRoute {
    RELAY,
    QUARANTINE,
    RELEASE_INBOUND,
    RELEASE_OUTBOUND,
    ENCRYPT_THEN_RELAY
}
