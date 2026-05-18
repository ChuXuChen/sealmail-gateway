package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.exceptionmail.ExceptionMail;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.ExceptionMailEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ExceptionMailMapper {

    private final ObjectMapper objectMapper;

    public ExceptionMailMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExceptionMailEntity toEntity(ExceptionMail mail) {
        ExceptionMailEntity entity = new ExceptionMailEntity();
        entity.setId(mail.getId());
        entity.setMessageId(mail.getMessageId());
        entity.setSubject(mail.getSubject());
        entity.setSenderEmail(mail.getSender().getValue());
        entity.setRecipients(serializeRecipients(mail.getRecipients()));
        entity.setDirection(mail.getDirection() != null ? mail.getDirection().name() : null);
        entity.setRemoteAddress(mail.getRemoteAddress());
        entity.setReason(mail.getReason().name());
        entity.setDetail(mail.getDetail());
        entity.setBlockedBy(mail.getBlockedBy());
        entity.setBlockComment(mail.getBlockComment());
        entity.setCreatedAt(mail.getCreatedAt());
        return entity;
    }

    public ExceptionMail toDomain(ExceptionMailEntity entity) {
        return toDomain(entity, new byte[0]);
    }

    public ExceptionMail toDomain(ExceptionMailEntity entity, byte[] rawContent) {
        return ExceptionMail.restore(
                entity.getId(),
                entity.getMessageId(),
                entity.getSubject(),
                new EmailAddress(entity.getSenderEmail()),
                deserializeRecipients(entity.getRecipients()),
                parseDirection(entity.getDirection()),
                entity.getRemoteAddress(),
                QuarantineReason.valueOf(entity.getReason()),
                entity.getDetail(),
                entity.getBlockedBy(),
                entity.getBlockComment(),
                entity.getCreatedAt(),
                rawContent == null ? new byte[0] : rawContent
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
            List<String> emails = recipients.stream()
                    .map(EmailAddress::getValue)
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(emails);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize exception mail recipients", e);
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
            throw new RuntimeException("Failed to deserialize exception mail recipients", e);
        }
    }

}
