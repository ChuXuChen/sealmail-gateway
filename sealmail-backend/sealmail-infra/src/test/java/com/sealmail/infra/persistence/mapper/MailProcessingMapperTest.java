package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.mailsecurity.MailProcessing;
import com.sealmail.domain.mailsecurity.MailProcessingStatusSnapshot;
import com.sealmail.domain.mailsecurity.RoutingDecision;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.domain.mailsecurity.AttachmentSecurityAction;
import com.sealmail.domain.mailsecurity.AttachmentSecurityFinding;
import com.sealmail.infra.persistence.entity.MailProcessingEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void roundTripsAttachmentSecuritySnapshot() {
        MailProcessing processing = MailProcessing.create(envelope(), MailDirection.OUTBOUND);
        processing.updateStatusSnapshot(new MailProcessingStatusSnapshot(
                MailProcessingStatusSnapshot.MailAuthStatus.pending(),
                MailProcessingStatusSnapshot.CertificateStatus.pending(),
                MailProcessingStatusSnapshot.SmimeStatus.pending(),
                MailProcessingStatusSnapshot.DlpStatus.pending(),
                new MailProcessingStatusSnapshot.AttachmentSecurityStatus(
                        MailProcessingStatusSnapshot.QUARANTINED,
                        AttachmentSecurityAction.QUARANTINE,
                        95,
                        1,
                        2048L,
                        List.of(new AttachmentSecurityFinding(
                                "HIGH_RISK_EXTENSION",
                                95,
                                AttachmentSecurityAction.QUARANTINE,
                                "Blocked extension detected",
                                "invoice.pdf.exe",
                                "exe",
                                "application/pdf",
                                "application/x-msdownload",
                                false,
                                false,
                                List.of("invoice.pdf.exe"))),
                        List.of("Declared MIME mismatch"),
                        null),
                MailProcessingStatusSnapshot.DeliveryStatus.pending(),
                MailProcessingStatusSnapshot.FinalDispositionStatus.pending(),
                MailProcessingStatusSnapshot.FailureStatus.pending()));

        MailProcessingEntity entity = mapper.toEntity(processing);
        MailProcessing restored = mapper.toDomain(entity);

        assertEquals(AttachmentSecurityAction.QUARANTINE,
                restored.getStatusSnapshot().attachmentSecurity().action());
        assertEquals("HIGH_RISK_EXTENSION",
                restored.getStatusSnapshot().attachmentSecurity().findings().getFirst().code());
        assertEquals("Declared MIME mismatch",
                restored.getStatusSnapshot().attachmentSecurity().warnings().getFirst());
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
