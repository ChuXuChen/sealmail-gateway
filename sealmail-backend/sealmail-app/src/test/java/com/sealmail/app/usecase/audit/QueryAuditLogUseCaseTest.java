package com.sealmail.app.usecase.audit;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.mapper.AuditDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryAuditLogUseCaseTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final QueryAuditLogUseCase useCase = new QueryAuditLogUseCase(repository, new AuditDtoMapper());

    @Test
    void searchResolvesCategoryAndSuccessFilter() {
        PageRequest pageRequest = PageRequest.builder().page(2).size(10).build();
        List<AuditLogType> expectedTypes = List.of(
                AuditLogType.USER_LOGIN,
                AuditLogType.USER_LOGIN_FAILED,
                AuditLogType.USER_LOGOUT,
                AuditLogType.USER_PASSWORD_CHANGED
        );
        when(repository.search(expectedTypes, false, 2, 10)).thenReturn(List.of());
        when(repository.countSearch(expectedTypes, false)).thenReturn(3L);

        var response = useCase.search("AUTH", null, false, pageRequest, auditor());

        assertEquals(3, response.getTotal());
        verify(repository).search(expectedTypes, false, 2, 10);
        verify(repository).countSearch(expectedTypes, false);
    }

    @Test
    void explicitTypeOverridesCategory() {
        PageRequest pageRequest = PageRequest.builder().page(1).size(20).build();
        List<AuditLogType> expectedTypes = List.of(AuditLogType.CERTIFICATE_REVOKED);
        when(repository.search(expectedTypes, null, 1, 20)).thenReturn(List.of());

        useCase.search("AUTH", AuditLogType.CERTIFICATE_REVOKED.name(), null, pageRequest, auditor());

        verify(repository).search(expectedTypes, null, 1, 20);
    }

    @Test
    void emailCategoryIncludesDlpViolations() {
        PageRequest pageRequest = PageRequest.builder().page(1).size(20).build();
        List<AuditLogType> expectedTypes = List.of(
                AuditLogType.EMAIL_RECEIVED,
                AuditLogType.EMAIL_DELIVERED,
                AuditLogType.EMAIL_RELEASED,
                AuditLogType.EMAIL_REJECTED,
                AuditLogType.EMAIL_QUARANTINED,
                AuditLogType.EMAIL_ENCRYPTED,
                AuditLogType.EMAIL_DECRYPTED,
                AuditLogType.EMAIL_SIGNED,
                AuditLogType.EMAIL_VERIFIED,
                AuditLogType.DLP_VIOLATION
        );
        when(repository.search(expectedTypes, null, 1, 20)).thenReturn(List.of());

        useCase.search("EMAIL", null, null, pageRequest, auditor());

        verify(repository).search(expectedTypes, null, 1, 20);
    }

    private static UserContext auditor() {
        return UserContext.builder()
                .userId("auditor-1")
                .username("auditor")
                .roles(Set.of("AUDITOR"))
                .build();
    }
}
