package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.MailProcessingEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailProcessingMapperTest {

    private final MailProcessingMapper mapper = new MailProcessingMapper(new ObjectMapper());

    @Test
    void serializesRoutingDecisionWithoutRawQuarantineDetail() {
        MailProcessing processing = MailProcessing.create(
                envelope(),
                MailDirection.OUTBOUND);
        processing.setRoutingDecision(new RoutingDecision.Quarantine(
                QuarantineReason.POLICY_VIOLATION,
                "customer body password=secret-value"));

        MailProcessingEntity entity = mapper.toEntity(processing);

        assertTrue(entity.getRoutingDecision().contains("\"type\":\"QUARANTINE\""));
        assertTrue(entity.getRoutingDecision().contains("\"detailPresent\":true"));
        assertFalse(entity.getRoutingDecision().contains("customer body"));
        assertFalse(entity.getRoutingDecision().contains("secret-value"));
        assertInstanceOf(RoutingDecision.Quarantine.class, mapper.toDomain(entity).getRoutingDecision());
    }

    private static MailEnvelope envelope() {
        return new MailEnvelope(
                "msg-1@example.com",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes());
    }
}
