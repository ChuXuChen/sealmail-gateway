package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.*;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.MailProcessingEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        entity.setRoutingDecision(serializeRoutingDecision(mailProcessing.getRoutingDecision()));
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
                entity.getId(), envelope, MailDirection.valueOf(entity.getDirection())
        );
        RoutingDecision routingDecision = deserializeRoutingDecision(entity.getRoutingDecision(), recipients);
        if (routingDecision != null) {
            processing.setRoutingDecision(routingDecision);
        }

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

    public String serializeRoutingDecision(RoutingDecision decision) {
        if (decision == null) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        if (decision instanceof RoutingDecision.OutboundEncrypt outboundEncrypt) {
            value.put("type", "OUTBOUND_ENCRYPT");
            value.put("recipientCount", outboundEncrypt.getRecipients().size());
        } else if (decision instanceof RoutingDecision.OutboundSign outboundSign) {
            value.put("type", "OUTBOUND_SIGN");
            value.put("recipientCount", outboundSign.getRecipients().size());
        } else if (decision instanceof RoutingDecision.InboundDecrypt) {
            value.put("type", "INBOUND_DECRYPT");
        } else if (decision instanceof RoutingDecision.InboundVerify) {
            value.put("type", "INBOUND_VERIFY");
        } else if (decision instanceof RoutingDecision.PassThrough) {
            value.put("type", "PASS_THROUGH");
        } else if (decision instanceof RoutingDecision.Quarantine quarantine) {
            String detail = quarantine.getDetail();
            value.put("type", "QUARANTINE");
            value.put("reason", quarantine.getReason().name());
            value.put("detailPresent", detail != null && !detail.isBlank());
            value.put("detailLength", detail != null ? detail.length() : 0);
        } else {
            value.put("type", decision.getClass().getSimpleName());
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize routing decision", e);
        }
    }

    private RoutingDecision deserializeRoutingDecision(String json, List<EmailAddress> recipients) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, new TypeReference<>() {});
            Object type = value.get("type");
            if (!(type instanceof String typeName)) {
                return null;
            }
            return switch (typeName) {
                case "OUTBOUND_ENCRYPT" -> new RoutingDecision.OutboundEncrypt(recipients);
                case "OUTBOUND_SIGN" -> new RoutingDecision.OutboundSign(recipients);
                case "INBOUND_DECRYPT" -> new RoutingDecision.InboundDecrypt();
                case "INBOUND_VERIFY" -> new RoutingDecision.InboundVerify();
                case "PASS_THROUGH" -> new RoutingDecision.PassThrough();
                case "QUARANTINE" -> new RoutingDecision.Quarantine(
                        QuarantineReason.valueOf(String.valueOf(value.get("reason"))),
                        null);
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }
}
