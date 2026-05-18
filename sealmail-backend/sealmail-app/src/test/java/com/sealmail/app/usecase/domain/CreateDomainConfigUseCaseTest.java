package com.sealmail.app.usecase.domain;

import com.sealmail.app.dto.request.CreateDomainConfigRequest;
import com.sealmail.app.mapper.DomainDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateDomainConfigUseCaseTest {

    @Test
    void localDomainUsesMandatoryPolicyAsDefault() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.existsByDomain("local.example")).thenReturn(false);
        when(repository.save(any(DomainConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateDomainConfigUseCase useCase = new CreateDomainConfigUseCase(repository, new DomainDtoMapper());

        CreateDomainConfigRequest request = new CreateDomainConfigRequest();
        request.setDomain("Local.Example.");
        request.setLocalDomain(true);

        var response = useCase.execute(request, admin());

        assertEquals("local.example", response.getDomain());
        assertEquals("MANDATORY", response.getEncryptionPolicy());
        verify(repository).existsByDomain("local.example");
    }

    @Test
    void validationAllowsTrailingDotBeforeNormalization() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            CreateDomainConfigRequest request = new CreateDomainConfigRequest();
            request.setDomain("Local.Example.");

            var violations = validatorFactory.getValidator().validate(request);

            assertEquals(Set.of(), violations);
        }
    }

    @Test
    void explicitPolicyOverridesLocalDomainDefault() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.existsByDomain("local.example")).thenReturn(false);
        when(repository.findByDomain("local.example")).thenReturn(Optional.empty());
        when(repository.save(any(DomainConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateDomainConfigUseCase useCase = new CreateDomainConfigUseCase(repository, new DomainDtoMapper());

        CreateDomainConfigRequest request = new CreateDomainConfigRequest();
        request.setDomain("local.example");
        request.setLocalDomain(true);
        request.setEncryptionPolicy("NO_ENCRYPTION");

        var response = useCase.execute(request, admin());

        assertEquals("NO_ENCRYPTION", response.getEncryptionPolicy());
    }

    @Test
    void deliveryTransportProfileDoesNotForceDefaultPort() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.existsByDomain("partner.example")).thenReturn(false);
        when(repository.save(any(DomainConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateDomainConfigUseCase useCase = new CreateDomainConfigUseCase(repository, new DomainDtoMapper());

        CreateDomainConfigRequest request = new CreateDomainConfigRequest();
        request.setDomain("partner.example");
        request.setLocalDomain(false);
        request.setDeliveryHost("smtp.partner.example");
        request.setDeliveryTransportProfile("SMTP_CLEAR");
        request.setDeliveryPort(587);

        var response = useCase.execute(request, admin());

        assertEquals("SMTP_CLEAR", response.getDeliveryTransportProfile());
        assertEquals(587, response.getDeliveryPort());
    }

    @Test
    void startTlsProfileCanUsePort25() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.existsByDomain("partner.example")).thenReturn(false);
        when(repository.save(any(DomainConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateDomainConfigUseCase useCase = new CreateDomainConfigUseCase(repository, new DomainDtoMapper());

        CreateDomainConfigRequest request = new CreateDomainConfigRequest();
        request.setDomain("partner.example");
        request.setLocalDomain(false);
        request.setDeliveryHost("smtp.partner.example");
        request.setDeliveryTransportProfile("SMTP_STARTTLS_STANDARD");
        request.setDeliveryPort(25);

        var response = useCase.execute(request, admin());

        assertEquals("SMTP_STARTTLS_STANDARD", response.getDeliveryTransportProfile());
        assertEquals(25, response.getDeliveryPort());
    }

    private static UserContext admin() {
        return UserContext.builder()
                .userId("admin")
                .email("admin@example.com")
                .roles(Set.of("ADMIN"))
                .build();
    }
}
