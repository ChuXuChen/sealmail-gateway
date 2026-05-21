package com.sealmail.domain.mailsecurity;

public enum MailProcessingErrorType {
    ROUTING,
    AUTHENTICATION,
    DECRYPTION,
    VERIFICATION,
    ATTACHMENT_SECURITY,
    DLP,
    SIGNING,
    ENCRYPTION,
    DKIM_SIGNING,
    RELAY,
    QUARANTINE,
    PIPELINE,
    UNKNOWN
}
