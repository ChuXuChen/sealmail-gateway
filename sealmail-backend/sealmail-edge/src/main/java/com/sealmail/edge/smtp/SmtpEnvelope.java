package com.sealmail.edge.smtp;

import java.util.ArrayList;
import java.util.List;

public final class SmtpEnvelope {
    private String sender;
    private final List<String> recipients = new ArrayList<>();

    public String sender() {
        return sender;
    }

    public List<String> recipients() {
        return List.copyOf(recipients);
    }

    public int recipientCount() {
        return recipients.size();
    }

    public boolean hasSender() {
        return sender != null;
    }

    public boolean hasRecipients() {
        return !recipients.isEmpty();
    }

    public void setSender(String sender) {
        this.sender = sender;
        this.recipients.clear();
    }

    public void addRecipient(String recipient) {
        this.recipients.add(recipient);
    }

    public void reset() {
        this.sender = null;
        this.recipients.clear();
    }
}
