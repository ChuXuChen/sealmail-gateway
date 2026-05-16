package com.sealmail.domain.mailsecurity;

public enum MailProcessingErrorType {
    ROUTING,
    AUTHENTICATION,
    DECRYPTION,
    VERIFICATION,
    DLP,
    SIGNING,
    ENCRYPTION,
    DKIM_SIGNING,
    RELAY,
    QUARANTINE,
    PIPELINE,
    UNKNOWN
}
