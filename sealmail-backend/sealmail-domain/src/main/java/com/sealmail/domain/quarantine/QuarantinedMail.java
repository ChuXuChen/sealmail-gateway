package com.sealmail.domain.quarantine;

import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.event.QuarantineCreated;
import com.sealmail.domain.quarantine.event.QuarantineReleaseRestored;
import com.sealmail.domain.quarantine.event.QuarantineReleased;
import com.sealmail.domain.quarantine.event.QuarantineRejected;
import com.sealmail.domain.shared.exception.DomainException;
import com.sealmail.domain.shared.model.AggregateRoot;
import com.sealmail.domain.shared.model.EmailAddress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuarantinedMail extends AggregateRoot<String> {

    private final String messageId;
    private final String subject;
    private final EmailAddress sender;
    private final List<EmailAddress> recipients;
    private final MailDirection direction;
    private final String remoteAddress;
    private final QuarantineReason reason;
    private final String detail;
    private final byte[] rawContent;
    private QuarantineStatus status;
    private final Instant createdAt;
    private Instant resolvedAt;
    private String processedBy;
    private String processComment;
    private String dlpEventId;
    private boolean falsePositive;
    private Instant falsePositiveAt;
    private String falsePositiveBy;
    private String falsePositiveComment;

    private QuarantinedMail(String id, String messageId, String subject, EmailAddress sender,
                            List<EmailAddress> recipients, MailDirection direction, String remoteAddress,
                            QuarantineReason reason, String detail, byte[] rawContent, Instant createdAt) {
        super(id);
        this.messageId = messageId;
        this.subject = subject;
        this.sender = sender;
        this.recipients = new ArrayList<>(recipients);
        this.direction = direction;
        this.remoteAddress = remoteAddress;
        this.reason = reason;
        this.detail = detail;
        this.rawContent = rawContent != null ? rawContent.clone() : new byte[0];
        this.status = QuarantineStatus.QUARANTINED;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static QuarantinedMail create(String id, String messageId, String subject,
                                         EmailAddress sender, List<EmailAddress> recipients,
                                         String remoteAddress, QuarantineReason reason, String detail) {
        return create(id, messageId, subject, sender, recipients, remoteAddress, reason, detail, null);
    }

    public static QuarantinedMail create(String id, String messageId, String subject,
                                         EmailAddress sender, List<EmailAddress> recipients,
                                         String remoteAddress, QuarantineReason reason, String detail,
                                         byte[] rawContent) {
        return create(id, messageId, subject, sender, recipients, null, remoteAddress, reason, detail, rawContent);
    }

    public static QuarantinedMail create(String id, String messageId, String subject,
                                         EmailAddress sender, List<EmailAddress> recipients,
                                         MailDirection direction, String remoteAddress,
                                         QuarantineReason reason, String detail, byte[] rawContent) {
        validateRequired(messageId, sender, recipients, reason);
        QuarantinedMail mail = new QuarantinedMail(id, messageId, subject, sender,
                recipients, direction, remoteAddress, reason, detail, rawContent, Instant.now());
        mail.registerEvent(new QuarantineCreated(id, messageId, sender, reason));
        return mail;
    }

    public static QuarantinedMail restore(String id, String messageId, String subject,
                                          EmailAddress sender, List<EmailAddress> recipients,
                                          String remoteAddress, QuarantineReason reason, String detail,
                                          QuarantineStatus status, Instant createdAt, Instant resolvedAt,
                                          String processedBy, String processComment, byte[] rawContent) {
        return restore(id, messageId, subject, sender, recipients, null, remoteAddress, reason, detail,
                status, createdAt, resolvedAt, processedBy, processComment, rawContent);
    }

    public static QuarantinedMail restore(String id, String messageId, String subject,
                                          EmailAddress sender, List<EmailAddress> recipients,
                                          MailDirection direction, String remoteAddress,
                                          QuarantineReason reason, String detail,
                                          QuarantineStatus status, Instant createdAt, Instant resolvedAt,
                                          String processedBy, String processComment, byte[] rawContent) {
        return restore(id, messageId, subject, sender, recipients, direction, remoteAddress, reason, detail,
                status, createdAt, resolvedAt, processedBy, processComment, rawContent, null,
                false, null, null, null);
    }

    public static QuarantinedMail restore(String id, String messageId, String subject,
                                          EmailAddress sender, List<EmailAddress> recipients,
                                          MailDirection direction, String remoteAddress,
                                          QuarantineReason reason, String detail,
                                          QuarantineStatus status, Instant createdAt, Instant resolvedAt,
                                          String processedBy, String processComment, byte[] rawContent,
                                          String dlpEventId, boolean falsePositive, Instant falsePositiveAt,
                                          String falsePositiveBy, String falsePositiveComment) {
        validateRequired(messageId, sender, recipients, reason);
        if (status == null) {
            throw new IllegalArgumentException("Quarantine status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        if (isResolvedStatus(status) && resolvedAt == null) {
            throw new IllegalArgumentException("ResolvedAt cannot be null for resolved quarantine mail");
        }
        if (isResolvedStatus(status) && (processedBy == null || processedBy.isBlank())) {
            throw new IllegalArgumentException("ProcessedBy cannot be blank for resolved quarantine mail");
        }
        QuarantinedMail mail = new QuarantinedMail(id, messageId, subject, sender,
                recipients, direction, remoteAddress, reason, detail, rawContent, createdAt);
        mail.status = status;
        mail.resolvedAt = resolvedAt;
        mail.processedBy = processedBy;
        mail.processComment = processComment;
        mail.dlpEventId = dlpEventId;
        mail.falsePositive = falsePositive;
        mail.falsePositiveAt = falsePositiveAt;
        mail.falsePositiveBy = falsePositiveBy;
        mail.falsePositiveComment = falsePositiveComment;
        return mail;
    }

    private static boolean isResolvedStatus(QuarantineStatus status) {
        return status == QuarantineStatus.RELEASED || status == QuarantineStatus.REJECTED;
    }

    private static void validateRequired(String messageId, EmailAddress sender,
                                         List<EmailAddress> recipients, QuarantineReason reason) {
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
            throw new IllegalArgumentException("Quarantine reason cannot be null");
        }
    }

    public void release(String releasedBy, String comment) {
        release(releasedBy, comment, false);
    }

    public void startRelease(String releasedBy, String comment, boolean force) {
        if (status != QuarantineStatus.QUARANTINED
                && !(force && status == QuarantineStatus.REJECTED)) {
            throw new DomainException("Cannot release mail that is not quarantined. Current status: " + status);
        }
        if (releasedBy == null || releasedBy.isBlank()) {
            throw new IllegalArgumentException("ReleasedBy cannot be blank");
        }
        this.status = QuarantineStatus.RELEASING;
        this.resolvedAt = null;
        this.processedBy = releasedBy;
        this.processComment = comment;
    }

    public void release(String releasedBy, String comment, boolean force) {
        if (status != QuarantineStatus.QUARANTINED
                && status != QuarantineStatus.RELEASING
                && !(force && status == QuarantineStatus.REJECTED)) {
            throw new DomainException("Cannot release mail that is not quarantined. Current status: " + status);
        }
        if (releasedBy == null || releasedBy.isBlank()) {
            throw new IllegalArgumentException("ReleasedBy cannot be blank");
        }
        this.status = QuarantineStatus.RELEASED;
        this.resolvedAt = Instant.now();
        this.processedBy = releasedBy;
        this.processComment = comment;
        registerEvent(new QuarantineReleased(getId(), releasedBy, comment));
    }

    public void restoreAfterFailedRelease(QuarantineStatus previousStatus, Instant previousResolvedAt,
                                          String previousProcessedBy, String previousProcessComment) {
        if (status != QuarantineStatus.RELEASING) {
            throw new DomainException("Cannot restore release attempt. Current status: " + status);
        }
        if (previousStatus != QuarantineStatus.QUARANTINED && previousStatus != QuarantineStatus.REJECTED) {
            throw new IllegalArgumentException("Previous status must be QUARANTINED or REJECTED");
        }
        this.status = previousStatus;
        if (previousStatus == QuarantineStatus.REJECTED) {
            this.resolvedAt = previousResolvedAt;
            this.processedBy = previousProcessedBy;
            this.processComment = previousProcessComment;
        } else {
            this.resolvedAt = null;
            this.processedBy = null;
            this.processComment = null;
        }
    }

    public void restoreReleaseForRetry(String restoredBy, String comment) {
        if (status != QuarantineStatus.RELEASING) {
            throw new DomainException("Cannot restore release attempt. Current status: " + status);
        }
        if (restoredBy == null || restoredBy.isBlank()) {
            throw new IllegalArgumentException("RestoredBy cannot be blank");
        }
        this.status = QuarantineStatus.QUARANTINED;
        this.resolvedAt = null;
        this.processedBy = null;
        this.processComment = null;
        registerEvent(new QuarantineReleaseRestored(getId(), restoredBy, comment));
    }

    public void reject(String rejectedBy, String comment) {
        if (status != QuarantineStatus.QUARANTINED) {
            throw new DomainException("Cannot reject mail that is not quarantined. Current status: " + status);
        }
        if (rejectedBy == null || rejectedBy.isBlank()) {
            throw new IllegalArgumentException("RejectedBy cannot be blank");
        }
        this.status = QuarantineStatus.REJECTED;
        this.resolvedAt = Instant.now();
        this.processedBy = rejectedBy;
        this.processComment = comment;
        registerEvent(new QuarantineRejected(getId(), rejectedBy, comment));
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

    public byte[] getRawContent() {
        return rawContent.clone();
    }

    public boolean hasRawContent() {
        return rawContent.length > 0;
    }

    public QuarantineStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public String getProcessComment() {
        return processComment;
    }

    public String getDlpEventId() {
        return dlpEventId;
    }

    public boolean isFalsePositive() {
        return falsePositive;
    }

    public Instant getFalsePositiveAt() {
        return falsePositiveAt;
    }

    public String getFalsePositiveBy() {
        return falsePositiveBy;
    }

    public String getFalsePositiveComment() {
        return falsePositiveComment;
    }
}
