package com.sealmail.infra.mail;

import com.sealmail.domain.mail.spi.MailMessageComposer;
import com.sealmail.domain.shared.model.EmailAddress;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Component
public class MimeMailMessageComposer implements MailMessageComposer {

    @Override
    public byte[] composeText(MailDraft draft) {
        String messageId = "<" + UUID.randomUUID() + "@sealmail.local>";
        String date = ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME);
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(draft.subject().getBytes(StandardCharsets.UTF_8))
                + "?=";

        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(draft.sender().getValue()).append("\r\n");
        sb.append("To: ").append(draft.recipients().stream()
                .map(EmailAddress::getValue)
                .reduce((left, right) -> left + ", " + right)
                .orElse(""))
                .append("\r\n");
        sb.append("Subject: ").append(encodedSubject).append("\r\n");
        sb.append("Date: ").append(date).append("\r\n");
        sb.append("Message-ID: ").append(messageId).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("\r\n");
        sb.append(Base64.getMimeEncoder().encodeToString(draft.content().getBytes(StandardCharsets.UTF_8)));
        sb.append("\r\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
