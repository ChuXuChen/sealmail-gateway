package com.sealmail.domain.mailsecurity;

public record MailContentSnapshot(
        MailEnvelope envelope,
        byte[] originalMailContent,
        String subject,
        boolean smimeEncrypted,
        String smimeEncryptionSuite
) {

    public MailContentSnapshot {
        if (envelope == null) {
            throw new IllegalArgumentException("Mail envelope cannot be null");
        }
        originalMailContent = originalMailContent != null ? originalMailContent.clone() : envelope.getRawContent();
    }

    @Override
    public byte[] originalMailContent() {
        return originalMailContent != null ? originalMailContent.clone() : new byte[0];
    }
}
