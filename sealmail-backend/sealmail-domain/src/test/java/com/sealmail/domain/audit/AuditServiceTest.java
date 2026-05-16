package com.sealmail.domain.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditServiceTest {

    @Test
    void recordUserActionUsesMatchingAuditType() {
        CapturingAuditLogRepository repository = new CapturingAuditLogRepository();
        AuditService service = new AuditService(repository);

        service.recordUserAction(
                "user-1",
                "admin",
                "127.0.0.1",
                AuditLogType.USER_LOGOUT.name(),
                "USER_ACCOUNT",
                "user-1",
                "logout"
        );

        assertEquals(AuditLogType.USER_LOGOUT, repository.saved.getType());
    }

    @Test
    void recordUserActionFallsBackToOtherForUnknownAction() {
        CapturingAuditLogRepository repository = new CapturingAuditLogRepository();
        AuditService service = new AuditService(repository);

        service.recordUserAction(
                "user-1",
                "admin",
                "127.0.0.1",
                "CUSTOM_ACTION",
                "USER_ACCOUNT",
                "user-1",
                "custom"
        );

        assertEquals(AuditLogType.OTHER, repository.saved.getType());
    }

    private static class CapturingAuditLogRepository implements AuditLogRepository {
        private AuditLog saved;

        @Override
        public AuditLog save(AuditLog auditLog) {
            this.saved = auditLog;
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
        public List<AuditLog> findByResource(String resourceType, String resourceId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AuditLog> findByTimeRange(java.time.Instant startTime, java.time.Instant endTime, int page, int size) {
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
        public long countByResource(String resourceType, String resourceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByTimeRange(java.time.Instant startTime, java.time.Instant endTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countSearch(List<AuditLogType> types, Boolean success) {
            throw new UnsupportedOperationException();
        }
    }
}
