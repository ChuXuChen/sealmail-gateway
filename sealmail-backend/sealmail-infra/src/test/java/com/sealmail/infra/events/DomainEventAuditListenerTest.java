package com.sealmail.infra.events;

import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.event.CertificateIssued;
import com.sealmail.domain.certificate.event.CertificateRevoked;
import com.sealmail.domain.dlp.DlpScopeType;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.domain.policy.event.MailAuthConfigChanged;
import com.sealmail.domain.quarantine.event.QuarantineReleased;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainEventAuditListenerTest {

    private final CapturingAuditLogRepository repository = new CapturingAuditLogRepository();
    private final DomainEventAuditListener listener = new DomainEventAuditListener(repository);

    @Test
    void recordsCertificateIssuedAuditLog() {
        listener.onDomainEvent(new CertificateIssued(
                new CertificateId("ABC123"),
                new EmailAddress("user@example.com")));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.CERTIFICATE_ISSUED, saved.getType());
        assertEquals("CERTIFICATE", saved.getResourceType());
        assertEquals("abc123", saved.getResourceId());
    }

    @Test
    void recordsCertificateRevokedAuditLog() {
        listener.onDomainEvent(new CertificateRevoked(
                new CertificateId("ABC123"),
                "key compromised"));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.CERTIFICATE_REVOKED, saved.getType());
        assertEquals("key compromised", saved.getDetail().substring(saved.getDetail().indexOf(':') + 2));
    }

    @Test
    void recordsQuarantineReleaseAuditLog() {
        listener.onDomainEvent(new QuarantineReleased(
                "q-1",
                "admin-1",
                "false positive"));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.EMAIL_RELEASED, saved.getType());
        assertEquals("QUARANTINE", saved.getResourceType());
        assertEquals("q-1", saved.getResourceId());
        assertEquals("admin-1", saved.getUserId());
    }

    @Test
    void recordsMailEncryptedAuditLog() {
        listener.onDomainEvent(new MailEncrypted(
                "msg-1@example.com",
                new EmailAddress("recipient@example.com"),
                new CertificateId("DEF456")));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.EMAIL_ENCRYPTED, saved.getType());
        assertEquals("EMAIL", saved.getResourceType());
        assertEquals("msg-1@example.com", saved.getResourceId());
    }

    @Test
    void recordsMailSignedAuditLog() {
        listener.onDomainEvent(new MailSigned(
                "msg-2@example.com",
                new EmailAddress("sender@example.com"),
                new CertificateId("ABCDEF")));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.EMAIL_SIGNED, saved.getType());
        assertEquals("EMAIL", saved.getResourceType());
        assertEquals("msg-2@example.com", saved.getResourceId());
    }

    @Test
    void recordsDlpPatternConfigAuditLog() {
        listener.onDomainEvent(new DlpPatternConfigChanged(
                "pattern-1",
                "UPDATE",
                "身份证号"));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.SYSTEM_CONFIG_CHANGED, saved.getType());
        assertEquals("DLP_PATTERN", saved.getResourceType());
        assertEquals("pattern-1", saved.getResourceId());
    }

    @Test
    void recordsDlpSelectionConfigAuditLog() {
        listener.onDomainEvent(new DlpSelectionConfigChanged(
                "selection-1",
                "CREATE",
                DlpScopeType.SENDER_DOMAIN,
                "example.com"));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.SYSTEM_CONFIG_CHANGED, saved.getType());
        assertEquals("DLP_SELECTION", saved.getResourceType());
        assertEquals("selection-1", saved.getResourceId());
    }

    @Test
    void recordsMailAuthConfigAuditLog() {
        listener.onDomainEvent(new MailAuthConfigChanged(
                "default",
                List.of("DKIM", "DMARC")));

        AuditLog saved = repository.single();
        assertEquals(AuditLogType.SYSTEM_CONFIG_CHANGED, saved.getType());
        assertEquals("MAIL_AUTH_CONFIG", saved.getResourceType());
        assertEquals("default", saved.getResourceId());
    }

    private static class CapturingAuditLogRepository implements AuditLogRepository {
        private final List<AuditLog> saved = new ArrayList<>();

        AuditLog single() {
            assertEquals(1, saved.size());
            return saved.getFirst();
        }

        @Override
        public AuditLog save(AuditLog auditLog) {
            saved.add(auditLog);
            return auditLog;
        }

        @Override
        public Optional<AuditLog> findById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> findByType(AuditLogType type, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> findByUserId(String userId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> findByTimeRange(Instant startTime, Instant endTime, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> findAll(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> search(List<AuditLogType> types, Boolean success, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByType(AuditLogType type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByUserId(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByTimeRange(Instant startTime, Instant endTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countSearch(List<AuditLogType> types, Boolean success) {
            throw new UnsupportedOperationException();
        }
    }
}
