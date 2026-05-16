package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.quarantine.QuarantineReason;

public record MailProcessingDecision(
        boolean signingRequired,
        boolean encryptionRequired,
        boolean mustEncrypt,
        boolean decryptionRequired,
        boolean verificationRequired,
        boolean dkimSigningRequired,
        Quarantine quarantine,
        DlpDecision dlpDecision
) {

    public MailProcessingDecision {
        dlpDecision = dlpDecision != null ? dlpDecision : DlpDecision.none();
    }

    public static MailProcessingDecision none() {
        return new MailProcessingDecision(false, false, false, false, false, false, null, DlpDecision.none());
    }

    public boolean requiresEncryption() {
        return encryptionRequired || mustEncrypt;
    }

    public boolean requiresQuarantine() {
        return quarantine != null;
    }

    public MailProcessingDecision withSigningRequired(boolean value) {
        return new MailProcessingDecision(value, encryptionRequired, mustEncrypt, decryptionRequired,
                verificationRequired, dkimSigningRequired, quarantine, dlpDecision);
    }

    public MailProcessingDecision withEncryptionRequired(boolean value) {
        return new MailProcessingDecision(signingRequired, value, mustEncrypt, decryptionRequired,
                verificationRequired, dkimSigningRequired, quarantine, dlpDecision);
    }

    public MailProcessingDecision withMustEncrypt(boolean value) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, value, decryptionRequired,
                verificationRequired, dkimSigningRequired, quarantine, dlpDecision);
    }

    public MailProcessingDecision withDecryptionRequired(boolean value) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, mustEncrypt, value,
                verificationRequired, dkimSigningRequired, quarantine, dlpDecision);
    }

    public MailProcessingDecision withVerificationRequired(boolean value) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, mustEncrypt, decryptionRequired,
                value, dkimSigningRequired, quarantine, dlpDecision);
    }

    public MailProcessingDecision withDkimSigningRequired(boolean value) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, mustEncrypt, decryptionRequired,
                verificationRequired, value, quarantine, dlpDecision);
    }

    public MailProcessingDecision withQuarantine(QuarantineReason reason, String detail) {
        return withQuarantine(reason != null ? reason.name() : null, detail);
    }

    public MailProcessingDecision withQuarantine(String reason, String detail) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, mustEncrypt, decryptionRequired,
                verificationRequired, dkimSigningRequired, new Quarantine(reason, detail), dlpDecision);
    }

    public MailProcessingDecision withDlpDecision(DlpDecision value) {
        return new MailProcessingDecision(signingRequired, encryptionRequired, mustEncrypt, decryptionRequired,
                verificationRequired, dkimSigningRequired, quarantine, value);
    }

    public record Quarantine(String reason, String detail) {
        public Quarantine {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Quarantine reason cannot be blank");
            }
        }
    }
}
