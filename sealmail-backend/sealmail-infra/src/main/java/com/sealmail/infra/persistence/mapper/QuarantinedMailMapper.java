package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.QuarantinedMailEntity;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class QuarantinedMailMapper {

    private final ObjectMapper objectMapper;

    public QuarantinedMailMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public QuarantinedMailEntity toEntity(QuarantinedMail quarantinedMail) {
        QuarantinedMailEntity entity = new QuarantinedMailEntity();
        entity.setId(quarantinedMail.getId());
        entity.setMessageId(quarantinedMail.getMessageId());
        entity.setSubject(quarantinedMail.getSubject());
        entity.setSenderEmail(quarantinedMail.getSender().getValue());
        entity.setRecipients(serializeRecipients(quarantinedMail.getRecipients()));
        entity.setDirection(quarantinedMail.getDirection() != null ? quarantinedMail.getDirection().name() : null);
        entity.setRemoteAddress(quarantinedMail.getRemoteAddress());
        entity.setReason(quarantinedMail.getReason().name());
        entity.setDetail(quarantinedMail.getDetail());
        entity.setRawContent(encodeRawContent(quarantinedMail.getRawContent()));
        entity.setStatus(quarantinedMail.getStatus().name());
        entity.setCreatedAt(quarantinedMail.getCreatedAt());
        entity.setResolvedAt(quarantinedMail.getResolvedAt());
        entity.setProcessedBy(quarantinedMail.getProcessedBy());
        entity.setProcessComment(quarantinedMail.getProcessComment());
        return entity;
    }

    public QuarantinedMail toDomain(QuarantinedMailEntity entity) {
        List<EmailAddress> recipients = deserializeRecipients(entity.getRecipients());
        EmailAddress sender = new EmailAddress(entity.getSenderEmail());

        return QuarantinedMail.restore(
                entity.getId(),
                entity.getMessageId(),
                entity.getSubject(),
                sender,
                recipients,
                parseDirection(entity.getDirection()),
                entity.getRemoteAddress(),
                QuarantineReason.valueOf(entity.getReason()),
                entity.getDetail(),
                QuarantineStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getResolvedAt(),
                entity.getProcessedBy(),
                entity.getProcessComment(),
                decodeRawContent(entity.getRawContent())
        );
    }

    private MailDirection parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return MailDirection.valueOf(direction);
    }

    private String serializeRecipients(List<EmailAddress> recipients) {
        try {
            List<String> emails = recipients.stream().map(EmailAddress::getValue).collect(Collectors.toList());
            return objectMapper.writeValueAsString(emails);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize recipients", e);
        }
    }

    private List<EmailAddress> deserializeRecipients(String json) {
        try {
            List<String> emails;
            try {
                emails = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                String actualJson = objectMapper.readValue(json, String.class);
                emails = objectMapper.readValue(actualJson, new TypeReference<List<String>>() {});
            }
            return emails.stream().map(EmailAddress::new).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize recipients", e);
        }
    }

    private String encodeRawContent(byte[] rawContent) {
        if (rawContent == null || rawContent.length == 0) {
            return null;
        }
        return Base64.getEncoder().encodeToString(rawContent);
    }

    private byte[] decodeRawContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(rawContent);
    }
}
