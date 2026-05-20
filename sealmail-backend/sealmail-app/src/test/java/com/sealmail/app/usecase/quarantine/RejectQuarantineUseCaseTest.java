package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.RejectQuarantineRequest;
import com.sealmail.app.exception.QuarantineStateException;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RejectQuarantineUseCaseTest {

    @Test
    void batchRejectValidatesAllItemsBeforeSavingAny() {
        InMemoryQuarantineRepository repository = new InMemoryQuarantineRepository(
                mail("q-1", QuarantineStatus.QUARANTINED),
                mail("q-2", QuarantineStatus.RELEASED)
        );
        RejectQuarantineUseCase useCase = new RejectQuarantineUseCase(
                repository,
                new QuarantineDtoMapper(),
                new PermissionChecker()
        );

        assertThrows(QuarantineStateException.class,
                () -> useCase.batchReject(List.of("q-1", "q-2"), new RejectQuarantineRequest(), admin()));

        assertEquals(0, repository.saveCount());
        assertEquals(QuarantineStatus.QUARANTINED, repository.status("q-1"));
    }

    @Test
    void batchRejectRejectsAllAfterValidationSucceeds() {
        InMemoryQuarantineRepository repository = new InMemoryQuarantineRepository(
                mail("q-1", QuarantineStatus.QUARANTINED),
                mail("q-2", QuarantineStatus.QUARANTINED)
        );
        RejectQuarantineUseCase useCase = new RejectQuarantineUseCase(
                repository,
                new QuarantineDtoMapper(),
                new PermissionChecker()
        );

        useCase.batchReject(List.of("q-1", "q-2"), new RejectQuarantineRequest(), admin());

        assertEquals(2, repository.saveCount());
        assertEquals(QuarantineStatus.REJECTED, repository.status("q-1"));
        assertEquals(QuarantineStatus.REJECTED, repository.status("q-2"));
    }

    private static QuarantinedMail mail(String id, QuarantineStatus status) {
        return QuarantinedMail.restore(
                id,
                "msg-" + id,
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "DLP QUARANTINE: detail",
                status,
                Instant.now(),
                status == QuarantineStatus.QUARANTINED ? null : Instant.now(),
                status == QuarantineStatus.QUARANTINED ? null : "operator",
                null,
                "raw".getBytes()
        );
    }

    private static UserContext admin() {
        return UserContext.builder()
                .userId("admin-1")
                .username("admin")
                .roles(Set.of("ADMIN"))
                .build();
    }

    private static final class InMemoryQuarantineRepository implements QuarantineRepository {
        private final Map<String, QuarantinedMail> mails = new LinkedHashMap<>();
        private int saveCount;

        private InMemoryQuarantineRepository(QuarantinedMail... mails) {
            for (QuarantinedMail mail : mails) {
                this.mails.put(mail.getId(), copy(mail));
            }
        }

        private int saveCount() {
            return saveCount;
        }

        private QuarantineStatus status(String id) {
            return mails.get(id).getStatus();
        }

        @Override
        public QuarantinedMail save(QuarantinedMail quarantinedMail) {
            saveCount++;
            mails.put(quarantinedMail.getId(), copy(quarantinedMail));
            return quarantinedMail;
        }

        @Override
        public Optional<QuarantinedMail> findById(String id) {
            return Optional.ofNullable(mails.get(id)).map(InMemoryQuarantineRepository::copy);
        }

        @Override
        public List<QuarantinedMail> findByStatus(QuarantineStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByMessageId(String messageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findAll(int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByStatus(QuarantineStatus status, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByStatuses(List<QuarantineStatus> statuses, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByReason(QuarantineReason reason, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByStatusAndReason(QuarantineStatus status, QuarantineReason reason, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<QuarantinedMail> findByStatusesAndReason(List<QuarantineStatus> statuses, QuarantineReason reason, int offset, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteCreatedBefore(Instant cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByStatus(QuarantineStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByStatuses(List<QuarantineStatus> statuses) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByReason(QuarantineReason reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByStatusAndReason(QuarantineStatus status, QuarantineReason reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByStatusesAndReason(List<QuarantineStatus> statuses, QuarantineReason reason) {
            throw new UnsupportedOperationException();
        }

        private static QuarantinedMail copy(QuarantinedMail mail) {
            return QuarantinedMail.restore(
                    mail.getId(),
                    mail.getMessageId(),
                    mail.getSubject(),
                    mail.getSender(),
                    mail.getRecipients(),
                    mail.getDirection(),
                    mail.getRemoteAddress(),
                    mail.getReason(),
                    mail.getDetail(),
                    mail.getStatus(),
                    mail.getCreatedAt(),
                    mail.getResolvedAt(),
                    mail.getProcessedBy(),
                    mail.getProcessComment(),
                    mail.getRawContent()
            );
        }
    }
}
