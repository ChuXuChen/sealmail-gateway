package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class MailEncrypted extends DomainEvent {

    private final String messageId;
    private final EmailAddress recipient;
    private final CertificateId certificateId;

    public MailEncrypted(String messageId, EmailAddress recipient, CertificateId certificateId) {
        this.messageId = messageId;
        this.recipient = recipient;
        this.certificateId = certificateId;
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getRecipient() {
        return recipient;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }
}
