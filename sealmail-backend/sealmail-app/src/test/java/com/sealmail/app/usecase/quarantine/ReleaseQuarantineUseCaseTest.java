package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.request.ReleaseQuarantineRequest;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import com.sealmail.domain.quarantine.QuarantinedMail;
import com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseQuarantineUseCaseTest {

    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final QuarantineMailReleaseRelay releaseRelay = mock(QuarantineMailReleaseRelay.class);
    private final ReleaseQuarantineUseCase useCase = new ReleaseQuarantineUseCase(
            repository,
            new QuarantineDtoMapper(),
            new PermissionChecker(),
            releaseRelay
    );

    @Test
    void releaseAllowsNullRequestBody() {
        QuarantinedMail mail = mail("raw".getBytes());
        when(repository.findById("q-1")).thenReturn(Optional.of(mail));

        var response = useCase.execute("q-1", null, admin());

        assertEquals(QuarantineStatus.RELEASED.name(), response.getStatus());
        assertEquals("admin-1", response.getResolvedBy());
        verify(releaseRelay).relay(mail, false);
        verify(repository).save(mail);
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
        verify(repository).save(mail);
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
}
