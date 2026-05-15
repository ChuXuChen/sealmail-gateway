package com.sealmail.domain.mailsecurity.event;

import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.model.EmailAddress;

public class MailSigned extends DomainEvent {

    private final String messageId;
    private final EmailAddress sender;
    private final CertificateId certificateId;

    public MailSigned(String messageId, EmailAddress sender, CertificateId certificateId) {
        this.messageId = messageId;
        this.sender = sender;
        this.certificateId = certificateId;
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public CertificateId getCertificateId() {
        return certificateId;
    }
}
