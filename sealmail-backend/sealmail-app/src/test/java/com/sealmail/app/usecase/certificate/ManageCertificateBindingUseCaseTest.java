package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.CertificateBindingRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateBinding;
import com.sealmail.domain.certificate.CertificateBindingPurpose;
import com.sealmail.domain.certificate.CertificateBindingRepository;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageCertificateBindingUseCaseTest {

    @Test
    void createsExplicitBindingForTrustedCertificateOwnerAndPurpose() {
        CertificateBindingRepository bindingRepository = mock(CertificateBindingRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        Certificate certificate = certificate("alice@example.com", EnumSet.of(KeyUsage.ENCRYPTION), false);
        certificate.trust();
        when(certificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.findById(any(CertificateId.class))).thenReturn(Optional.of(certificate));
        when(bindingRepository.findByOwnerAndPurpose(any(), any())).thenReturn(Optional.empty());
        when(bindingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ManageCertificateBindingUseCase useCase = useCase(bindingRepository, certificateRepository);

        var response = useCase.upsert(new CertificateBindingRequest(
                        "alice@example.com",
                        certificate.getId().getThumbprint(),
                        "ENCRYPTION",
                        true),
                admin());

        assertEquals("alice@example.com", response.ownerEmail());
        assertEquals("ENCRYPTION", response.purpose());
        assertEquals(certificate.getId().getThumbprint(), response.certificateId());
        verify(bindingRepository).save(any(CertificateBinding.class));
    }

    @Test
    void rejectsSigningBindingWhenCertificateHasNoPrivateKey() {
        CertificateBindingRepository bindingRepository = mock(CertificateBindingRepository.class);
        CertificateRepository certificateRepository = mock(CertificateRepository.class);
        Certificate certificate = certificate("alice@example.com", EnumSet.of(KeyUsage.SIGNING), false);
        certificate.trust();
        when(certificateRepository.findById(any(CertificateId.class))).thenReturn(Optional.of(certificate));

        ManageCertificateBindingUseCase useCase = useCase(bindingRepository, certificateRepository);

        assertThrows(BusinessException.class, () -> useCase.upsert(new CertificateBindingRequest(
                        "alice@example.com",
                        certificate.getId().getThumbprint(),
                        "SIGNING",
                        true),
                admin()));
    }

    private static ManageCertificateBindingUseCase useCase(CertificateBindingRepository bindingRepository,
                                                           CertificateRepository certificateRepository) {
        return new ManageCertificateBindingUseCase(
                bindingRepository,
                certificateRepository,
                new PermissionChecker(),
                new CertificateChainService(certificateRepository));
    }

    private static UserContext admin() {
        return UserContext.builder()
                .userId("admin-1")
                .username("admin")
                .roles(Set.of("ADMIN"))
                .build();
    }

    private static Certificate certificate(String owner, EnumSet<KeyUsage> usages, boolean privateKey) {
        Certificate certificate = Certificate.importCertificate(
                new CertificateId(java.util.UUID.randomUUID().toString()),
                new EmailAddress(owner),
                "pem",
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                usages,
                "CN=issuer",
                "CN=subject",
                BigInteger.ONE,
                "ski");
        certificate.setAlgorithm("RSA");
        if (privateKey) {
            certificate.setPrivateKeySecretRef("test-secret:" + certificate.getId().getThumbprint());
        }
        return certificate;
    }
}
