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
        entity.setRecipients(mailProcessing.getEnvelope().getRecipients().stream()
                .map(EmailAddress::getValue)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        entity.setRemoteHost(mailProcessing.getEnvelope().getRemoteHost());
        entity.setHelo(mailProcessing.getEnvelope().getHelo());
        entity.setReceivedAt(mailProcessing.getEnvelope().getReceivedAt());
        entity.setDirection(mailProcessing.getDirection().name());
        entity.setRoutingDecision(serializeRoutingDecision(mailProcessing.getRoutingDecision()));
        entity.setResult(mailProcessing.getResult() != null ? mailProcessing.getResult().name() : null);
        return entity;
    }

    public MailProcessing toDomain(MailProcessingEntity entity) {
        List<EmailAddress> recipients = entity.getRecipients().stream()
                .map(EmailAddress::new)
                .toList();
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
