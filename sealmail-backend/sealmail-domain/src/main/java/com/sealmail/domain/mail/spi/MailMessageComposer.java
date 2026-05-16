package com.sealmail.domain.mail.spi;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;

public interface MailMessageComposer {

    byte[] composeText(MailDraft draft);

    record MailDraft(
            EmailAddress sender,
            List<EmailAddress> recipients,
            String subject,
            String content
    ) {
        public MailDraft {
            if (sender == null) {
                throw new IllegalArgumentException("sender must not be null");
            }
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
            subject = subject == null ? "" : subject;
            content = content == null ? "" : content;
        }
    }
}
