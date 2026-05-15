package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.*;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.MailProcessingEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MailProcessingMapper {

    private final ObjectMapper objectMapper;

    public MailProcessingMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MailProcessingEntity toEntity(MailProcessing mailProcessing) {
        MailProcessingEntity entity = new MailProcessingEntity();
        entity.setId(mailProcessing.getId());
        entity.setMessageId(mailProcessing.getEnvelope().getMessageId());
        entity.setSenderEmail(mailProcessing.getEnvelope().getSender().getValue());
        entity.setRecipients(serializeRecipients(mailProcessing.getEnvelope().getRecipients()));
        entity.setRemoteHost(mailProcessing.getEnvelope().getRemoteHost());
        entity.setHelo(mailProcessing.getEnvelope().getHelo());
        entity.setReceivedAt(mailProcessing.getEnvelope().getReceivedAt());
        entity.setDirection(mailProcessing.getDirection().name());
        entity.setResult(mailProcessing.getResult() != null ? mailProcessing.getResult().name() : null);
        return entity;
    }

    public MailProcessing toDomain(MailProcessingEntity entity) {
        List<EmailAddress> recipients = deserializeRecipients(entity.getRecipients());
        EmailAddress sender = new EmailAddress(entity.getSenderEmail());

        MailEnvelope envelope = new MailEnvelope(
                entity.getMessageId(), sender, recipients,
                entity.getRemoteHost(), entity.getHelo(), entity.getReceivedAt()
        );

        MailProcessing processing = MailProcessing.create(
                envelope, MailDirection.valueOf(entity.getDirection())
        );

        if (entity.getResult() != null) {
            try {
                processing.completeProcessing(ProcessingResult.valueOf(entity.getResult()));
            } catch (Exception ignored) {}
        }

        processing.clearDomainEvents();
        return processing;
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
                // First try: direct deserialization
                emails = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // Second try: value was double-encoded, need to decode string first
                String actualJson = objectMapper.readValue(json, String.class);
                emails = objectMapper.readValue(actualJson, new TypeReference<List<String>>() {});
            }
            return emails.stream().map(EmailAddress::new).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize recipients: " + json, e);
        }
    }
}
