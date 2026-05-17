package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.ReleaseQuarantineRequest;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseQuarantineUseCaseTest {

    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final QuarantineMailReleaseRelay releaseRelay = mock(QuarantineMailReleaseRelay.class);
    private final QuarantinePolicyPort quarantinePolicyPort = mock(QuarantinePolicyPort.class);
    private final PlatformTransactionManager transactionManager = new ImmediatePlatformTransactionManager();
    private final ReleaseQuarantineUseCase useCase = new ReleaseQuarantineUseCase(
            repository,
            new QuarantineDtoMapper(),
            new PermissionChecker(),
            releaseRelay,
            quarantinePolicyPort,
            transactionManager
    );

    ReleaseQuarantineUseCaseTest() {
        when(quarantinePolicyPort.getSettings()).thenReturn(new QuarantinePolicyPort.QuarantinePolicySettings(
                30,
                false,
                false,
                java.time.Instant.now()));
    }

    @Test
    void releaseAllowsNullRequestBody() {
        QuarantinedMail mail = mail("raw".getBytes());
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        var response = useCase.execute("q-1", null, admin());

        assertEquals(QuarantineStatus.RELEASED.name(), response.getStatus());
        assertEquals("admin-1", response.getResolvedBy());
        verify(releaseRelay).relay(mail, false);
        verify(repository, times(2)).save(mail);
    }

    @Test
    void doesNotMarkReleasedWhenOriginalContentIsMissing() {
        QuarantinedMail mail = mail(new byte[0]);
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        assertThrows(MissingQuarantineMailContentException.class,
                () -> useCase.execute("q-1", new ReleaseQuarantineRequest(), admin()));

        assertEquals(QuarantineStatus.QUARANTINED, mail.getStatus());
        verify(releaseRelay, never()).relay(mail, false);
        verify(repository, never()).save(mail);
    }

    @Test
    void passesEncryptBeforeReleaseToRelay() {
        QuarantinedMail mail = mail("raw".getBytes());
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));
        ReleaseQuarantineRequest request = ReleaseQuarantineRequest.builder()
                .encryptBeforeRelease(true)
                .build();

        var response = useCase.execute("q-1", request, admin());

        assertEquals(QuarantineStatus.RELEASED.name(), response.getStatus());
        assertEquals("Encrypted before release", response.getResolutionComment());
        verify(releaseRelay).relay(mail, true);
        verify(repository, times(2)).save(mail);
    }

    @Test
    void doesNotMarkReleasedWhenRelayFails() {
        QuarantinedMail mail = mail("raw".getBytes());
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));
        doThrow(new RuntimeException("relay failed")).when(releaseRelay).relay(mail, false);

        assertThrows(RuntimeException.class,
                () -> useCase.execute("q-1", new ReleaseQuarantineRequest(), admin()));

        assertEquals(QuarantineStatus.QUARANTINED, mail.getStatus());
        verify(repository, times(2)).save(mail);
    }

    @Test
    void finalReleaseSaveFailureLeavesMailReleasingAndPreventsDuplicateRelay() {
        InMemoryQuarantineRepository repository = new InMemoryQuarantineRepository(mail("raw".getBytes()));
        repository.failOnSave(2);
        ReleaseQuarantineUseCase useCase = new ReleaseQuarantineUseCase(
                repository,
                new QuarantineDtoMapper(),
                new PermissionChecker(),
                releaseRelay,
                quarantinePolicyPort,
                transactionManager
        );

        assertThrows(RuntimeException.class,
                () -> useCase.execute("q-1", new ReleaseQuarantineRequest(), admin()));

        assertEquals(QuarantineStatus.RELEASING, repository.storedStatus());
        verify(releaseRelay).relay(org.mockito.ArgumentMatchers.any(QuarantinedMail.class), org.mockito.ArgumentMatchers.eq(false));

        reset(releaseRelay);

        assertThrows(com.sealmail.app.exception.QuarantineStateException.class,
                () -> useCase.execute("q-1", new ReleaseQuarantineRequest(), admin()));
        verify(releaseRelay, never()).relay(org.mockito.ArgumentMatchers.any(QuarantinedMail.class), anyBoolean());
    }

    private static QuarantinedMail mail(byte[] rawContent) {
        return QuarantinedMail.create(
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
    }

    private static UserContext admin() {
        return UserContext.builder()
                .userId("admin-1")
                .username("admin")
                .roles(Set.of("ADMIN"))
                .build();
    }

    private static final class ImmediatePlatformTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }

    private static final class InMemoryQuarantineRepository implements QuarantineRepository {
        private QuarantinedMail stored;
        private int saveCount;
        private int failOnSave = -1;

        private InMemoryQuarantineRepository(QuarantinedMail stored) {
            this.stored = copy(stored);
        }

        private void failOnSave(int saveCount) {
            this.failOnSave = saveCount;
        }

        private QuarantineStatus storedStatus() {
            return stored.getStatus();
        }

        @Override
        public QuarantinedMail save(QuarantinedMail quarantinedMail) {
            saveCount++;
            if (saveCount == failOnSave) {
                throw new RuntimeException("save failed");
            }
            stored = copy(quarantinedMail);
            return quarantinedMail;
        }

        @Override
        public Optional<QuarantinedMail> findById(String id) {
            if (stored != null && stored.getId().equals(id)) {
                return Optional.of(copy(stored));
            }
            return Optional.empty();
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
