package com.sealmail.domain.mailsecurity;

import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.domain.shared.model.ValueObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MailEnvelope extends ValueObject {

    /**
     * RFC 5322 message-id: angle-bracketed id@domain.
     * We accept both bare "id@domain" and "<id@domain>" forms,
     * normalising to the bare form internally.
     */
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile(
            "^<?[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@[A-Za-z0-9.-]+>?$"
    );

    private static final int MAX_RECIPIENTS = 100; // RFC 5321 practical limit

    private final String messageId;
    private final EmailAddress sender;
    private final List<EmailAddress> recipients;
    private final String remoteHost;
    private final String helo;
    private final Instant receivedAt;
    private final byte[] rawContent;

    public MailEnvelope(String messageId, EmailAddress sender, List<EmailAddress> recipients,
                        String remoteHost, String helo, Instant receivedAt) {
        this(messageId, sender, recipients, remoteHost, helo, receivedAt, null);
    }

    public MailEnvelope(String messageId, EmailAddress sender, List<EmailAddress> recipients,
                        String remoteHost, String helo, Instant receivedAt, byte[] rawContent) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("MessageId cannot be blank");
        }
        if (!MESSAGE_ID_PATTERN.matcher(messageId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Message-Id format per RFC 5322: " + messageId);
        }
        // Normalise: strip angle brackets for internal use
        this.messageId = messageId.trim().replaceAll("^<|>$", "");
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("Recipients cannot be empty");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("Recipients exceed maximum of " + MAX_RECIPIENTS);
        }
        this.sender = sender;
        this.recipients = new ArrayList<>(recipients);
        this.remoteHost = remoteHost;
        this.helo = helo != null ? helo : "";
        this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        this.rawContent = rawContent != null ? rawContent.clone() : new byte[0];
    }

    public String getMessageId() {
        return messageId;
    }

    public EmailAddress getSender() {
        return sender;
    }

    public List<EmailAddress> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getHelo() {
        return helo;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public byte[] getRawContent() {
        return rawContent != null ? rawContent.clone() : new byte[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailEnvelope that = (MailEnvelope) o;
        return messageId.equals(that.messageId) && sender.equals(that.sender) &&
                recipients.equals(that.recipients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, sender, recipients);
    }
}
