package com.sealmail.app.usecase.quarantine;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.mapper.QuarantineDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.quarantine.QuarantineReason;
import com.sealmail.domain.quarantine.QuarantineRepository;
import com.sealmail.domain.quarantine.QuarantineStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryQuarantineUseCaseTest {

    private final QuarantineRepository repository = mock(QuarantineRepository.class);
    private final QueryQuarantineUseCase useCase = new QueryQuarantineUseCase(
            repository,
            new QuarantineDtoMapper(),
            new PermissionChecker()
    );

    @Test
    void usesOneBasedPageNumberForOffset() {
        PageRequest pageRequest = PageRequest.builder().page(1).size(20).build();
        when(repository.findByStatus(QuarantineStatus.QUARANTINED, 0, 20)).thenReturn(List.of());
        when(repository.countByStatus(QuarantineStatus.QUARANTINED)).thenReturn(0L);

        var response = useCase.findAll(pageRequest, admin());

        assertEquals(1, response.getPage());
        verify(repository).findByStatus(QuarantineStatus.QUARANTINED, 0, 20);
    }

    @Test
    void appliesReasonFilterToItemsAndTotal() {
        PageRequest pageRequest = PageRequest.builder().page(2).size(10).build();
        when(repository.findByStatusAndReason(QuarantineStatus.QUARANTINED, QuarantineReason.EMAIL_AUTH_FAILED, 10, 10))
                .thenReturn(List.of());
        when(repository.countByStatusAndReason(QuarantineStatus.QUARANTINED, QuarantineReason.EMAIL_AUTH_FAILED))
                .thenReturn(12L);

        var response = useCase.findAll(pageRequest, QuarantineReason.EMAIL_AUTH_FAILED, admin());

        assertEquals(12, response.getTotal());
        verify(repository).findByStatusAndReason(QuarantineStatus.QUARANTINED, QuarantineReason.EMAIL_AUTH_FAILED, 10, 10);
        verify(repository).countByStatusAndReason(QuarantineStatus.QUARANTINED, QuarantineReason.EMAIL_AUTH_FAILED);
    }

    private static UserContext admin() {
        return UserContext.builder()
                .userId("admin-1")
                .username("admin")
                .roles(Set.of("ADMIN"))
                .build();
    }
}
