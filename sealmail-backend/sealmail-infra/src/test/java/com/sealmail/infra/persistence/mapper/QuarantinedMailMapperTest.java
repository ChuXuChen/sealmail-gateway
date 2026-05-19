package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailsecurity.MailDirection;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.QuarantinedMailEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarantinedMailMapperTest {

    private final QuarantinedMailMapper mapper = new QuarantinedMailMapper(new ObjectMapper());

    @Test
    void toDomainPreservesStoredStatusAndTimes() {
        Instant createdAt = Instant.parse("2026-05-14T08:00:00Z");
        Instant resolvedAt = Instant.parse("2026-05-14T09:00:00Z");
        byte[] rawContent = "raw-mail".getBytes();

        QuarantinedMailEntity entity = new QuarantinedMailEntity();
        entity.setId("q-1");
        entity.setMessageId("msg-1");
        entity.setSubject("subject");
        entity.setSenderEmail("sender@example.com");
        entity.setRecipients("[\"recipient@example.com\"]");
        entity.setDirection(MailDirection.OUTBOUND.name());
        entity.setRemoteAddress("127.0.0.1");
        entity.setReason(QuarantineReason.EMAIL_AUTH_FAILED.name());
        entity.setDetail("detail");
        entity.setStatus(QuarantineStatus.RELEASED.name());
        entity.setCreatedAt(createdAt);
        entity.setResolvedAt(resolvedAt);
        entity.setProcessedBy("admin");
        entity.setProcessComment("ok");

        QuarantinedMail mail = mapper.toDomain(entity, rawContent);

        assertEquals(QuarantineStatus.RELEASED, mail.getStatus());
        assertEquals(MailDirection.OUTBOUND, mail.getDirection());
        assertEquals(createdAt, mail.getCreatedAt());
        assertEquals(resolvedAt, mail.getResolvedAt());
        assertEquals("admin", mail.getProcessedBy());
        assertArrayEquals(rawContent, mail.getRawContent());
        assertEquals(0, mail.getDomainEvents().size());
    }

    @Test
    void toEntityDoesNotStoreRawContentInline() {
        byte[] rawContent = "raw-mail".getBytes();
        QuarantinedMail mail = QuarantinedMail.create(
                "q-1",
                "msg-1",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "DLP QUARANTINE: detail",
                rawContent
        );

        QuarantinedMailEntity entity = mapper.toEntity(mail);

        assertNull(entity.getRawContentId());
        assertEquals(null, entity.getDirection());
    }

    @Test
    void toDomainWithRawContentMarkerPreservesHasRawContentWithoutLoadingMailBody() {
        QuarantinedMailEntity entity = entity(QuarantineStatus.QUARANTINED);
        entity.setRawContentId("raw-1");

        QuarantinedMail mail = mapper.toDomainWithRawContentMarker(entity);

        assertTrue(mail.hasRawContent());
        assertEquals(1, mail.getRawContent().length);
    }

    private static QuarantinedMailEntity entity(QuarantineStatus status) {
        QuarantinedMailEntity entity = new QuarantinedMailEntity();
        entity.setId("q-1");
        entity.setMessageId("msg-1");
        entity.setSubject("subject");
        entity.setSenderEmail("sender@example.com");
        entity.setRecipients("[\"recipient@example.com\"]");
        entity.setDirection(MailDirection.OUTBOUND.name());
        entity.setRemoteAddress("127.0.0.1");
        entity.setReason(QuarantineReason.EMAIL_AUTH_FAILED.name());
        entity.setDetail("detail");
        entity.setStatus(status.name());
        entity.setCreatedAt(Instant.parse("2026-05-14T08:00:00Z"));
        return entity;
    }
}
