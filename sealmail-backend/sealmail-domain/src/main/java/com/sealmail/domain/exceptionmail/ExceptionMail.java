package com.sealmail.domain.exceptionmail;

import com.sealmail.domain.exceptionmail.event.ExceptionMailCreated;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.AggregateRoot;
import com.sealmail.domain.shared.model.EmailAddress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExceptionMail extends AggregateRoot<String> {

    private final String messageId;
    private final String subject;
    private final EmailAddress sender;
    private final List<EmailAddress> recipients;
    private final MailDirection direction;
    private final String remoteAddress;
    private final QuarantineReason reason;
    private final String detail;
    private final String blockedBy;
    private final String blockComment;
    private final byte[] rawContent;
    private final Instant createdAt;

    private ExceptionMail(String id,
                          String messageId,
                          String subject,
                          EmailAddress sender,
                          List<EmailAddress> recipients,
                          MailDirection direction,
                          String remoteAddress,
                          QuarantineReason reason,
                          String detail,
                          String blockedBy,
                          String blockComment,
                          byte[] rawContent,
                          Instant createdAt) {
        super(id);
        this.messageId = messageId;
        this.subject = subject;
        this.sender = sender;
        this.recipients = new ArrayList<>(recipients);
        this.direction = direction;
        this.remoteAddress = remoteAddress;
        this.reason = reason;
        this.detail = detail;
        this.blockedBy = blockedBy;
        this.blockComment = blockComment;
        this.rawContent = rawContent != null ? rawContent.clone() : new byte[0];
        this.createdAt = createdAt;
    }

    public static ExceptionMail create(String id,
                                       String messageId,
                                       String subject,
                                       EmailAddress sender,
                                       List<EmailAddress> recipients,
                                       MailDirection direction,
                                       String remoteAddress,
                                       QuarantineReason reason,
                                       String detail,
                                       String blockComment) {
        return create(id, messageId, subject, sender, recipients, direction, remoteAddress, reason, detail,
                blockComment, null);
    }

    public static ExceptionMail create(String id,
                                       String messageId,
                                       String subject,
                                       EmailAddress sender,
                                       List<EmailAddress> recipients,
                                       MailDirection direction,
                                       String remoteAddress,
                                       QuarantineReason reason,
                                       String detail,
                                       String blockComment,
                                       byte[] rawContent) {
        validateRequired(messageId, sender, recipients, reason);
        ExceptionMail mail = new ExceptionMail(
                id,
                messageId,
                subject,
                sender,
                recipients,
                direction,
                remoteAddress,
                reason,
                detail,
                "system",
                blockComment,
                rawContent,
                Instant.now()
        );
        mail.registerEvent(new ExceptionMailCreated(id, messageId, reason, detail));
        return mail;
    }

    public static ExceptionMail restore(String id,
                                        String messageId,
                                        String subject,
                                        EmailAddress sender,
                                        List<EmailAddress> recipients,
                                        MailDirection direction,
                                        String remoteAddress,
                                        QuarantineReason reason,
                                        String detail,
                                        String blockedBy,
                                        String blockComment,
                                        Instant createdAt) {
        return restore(id, messageId, subject, sender, recipients, direction, remoteAddress, reason, detail,
                blockedBy, blockComment, createdAt, null);
    }

    public static ExceptionMail restore(String id,
                                        String messageId,
                                        String subject,
                                        EmailAddress sender,
                                        List<EmailAddress> recipients,
                                        MailDirection direction,
                                        String remoteAddress,
                                        QuarantineReason reason,
                                        String detail,
                                        String blockedBy,
                                        String blockComment,
                                        Instant createdAt,
                                        byte[] rawContent) {
        validateRequired(messageId, sender, recipients, reason);
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        return new ExceptionMail(
                id,
                messageId,
                subject,
                sender,
                recipients,
                direction,
                remoteAddress,
                reason,
                detail,
                blockedBy,
                blockComment,
                rawContent,
                createdAt
        );
    }

    private static void validateRequired(String messageId,
                                         EmailAddress sender,
                                         List<EmailAddress> recipients,
                                         QuarantineReason reason) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("MessageId cannot be blank");
        }
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("Recipients cannot be empty");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Exception reason cannot be null");
        }
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSubject() {
        return subject;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public List<EmailAddress> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }

    public MailDirection getDirection() {
        return direction;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public QuarantineReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public String getBlockedBy() {
        return blockedBy;
    }

    public String getBlockComment() {
        return blockComment;
    }

    public byte[] getRawContent() {
        return rawContent.clone();
    }

    public boolean hasRawContent() {
        return rawContent.length > 0;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
