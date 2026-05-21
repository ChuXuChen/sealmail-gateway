package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.MailProcessingErrorType;

public enum MailFlowStep {
    ROUTING("routing", MailProcessingErrorType.ROUTING),
    MAIL_AUTH("mail-auth", MailProcessingErrorType.AUTHENTICATION),
    DECRYPT("decrypt", MailProcessingErrorType.DECRYPTION),
    VERIFY_SIGNATURE("verify-signature", MailProcessingErrorType.VERIFICATION),
    ATTACHMENT_SECURITY("attachment-security", MailProcessingErrorType.ATTACHMENT_SECURITY),
    DLP("dlp", MailProcessingErrorType.DLP),
    SIGN("sign", MailProcessingErrorType.SIGNING),
    ENCRYPT("encrypt", MailProcessingErrorType.ENCRYPTION),
    DKIM_SIGN("dkim-sign", MailProcessingErrorType.DKIM_SIGNING),
    QUARANTINE("quarantine", MailProcessingErrorType.QUARANTINE),
    RELAY("relay", MailProcessingErrorType.RELAY);

    private final String stepName;
    private final MailProcessingErrorType errorType;

    MailFlowStep(String stepName, MailProcessingErrorType errorType) {
        this.stepName = stepName;
        this.errorType = errorType;
    }

    public String stepName() {
        return stepName;
    }

    public MailProcessingErrorType errorType() {
        return errorType;
    }
}
