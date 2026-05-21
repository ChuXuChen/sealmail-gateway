package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.RepairQuarantineReleaseRequest;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.AppPermissionEvaluator;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepairQuarantineReleaseUseCaseTest {

    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final RepairQuarantineReleaseUseCase useCase = new RepairQuarantineReleaseUseCase(
            repository,
            new QuarantineDtoMapper(),
            new PermissionChecker(new AppPermissionEvaluator()));

    @Test
    void completeMarksReleasingMailReleasedWithoutRelay() {
        QuarantinedMail mail = releasingMail();
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        var response = useCase.complete("q-1", request("admin-2", "verified delivered"), admin());

        assertEquals(QuarantineStatus.RELEASED.name(), response.getStatus());
        assertEquals("admin-2", response.getResolvedBy());
        assertEquals("verified delivered", response.getResolutionComment());
        verify(repository).save(mail);
    }

    @Test
    void restoreMarksReleasingMailQuarantinedForRetry() {
        QuarantinedMail mail = releasingMail();
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        var response = useCase.restore("q-1", request("admin-2", "verified not delivered"), admin());

        assertEquals(QuarantineStatus.QUARANTINED.name(), response.getStatus());
        verify(repository).save(mail);
    }

    @Test
    void repairRejectsNonReleasingMail() {
        QuarantinedMail mail = mail();
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        assertThrows(com.sealmail.app.exception.QuarantineStateException.class,
                () -> useCase.complete("q-1", request("admin-2", "verified delivered"), admin()));
    }

    private static RepairQuarantineReleaseRequest request(String operator, String comment) {
        return RepairQuarantineReleaseRequest.builder()
                .operator(operator)
                .comment(comment)
                .build();
    }

    private static QuarantinedMail releasingMail() {
        QuarantinedMail mail = mail();
        mail.startRelease("admin-1", "release", false);
        return mail;
    }

    private static QuarantinedMail mail() {
        return QuarantinedMail.create(
                "q-1",
                "msg-1",
                "subject",
                new EmailAddress("sender@example.com"),
                List.of(new EmailAddress("recipient@example.com")),
                "127.0.0.1",
                QuarantineReason.POLICY_VIOLATION,
                "DLP QUARANTINE: detail",
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
}
