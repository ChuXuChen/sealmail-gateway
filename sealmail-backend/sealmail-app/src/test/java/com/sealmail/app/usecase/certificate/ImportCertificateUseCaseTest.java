package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.CertificateException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.AppPermissionEvaluator;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.certificate.spi.CertificateValidator;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportCertificateUseCaseTest {

    private final InMemoryCertificateRepository repository = new InMemoryCertificateRepository();
    private final FakeCertificateCryptoPort cryptoPort = new FakeCertificateCryptoPort();
    private final FakeKeyManagementPort keyManagementPort = new FakeKeyManagementPort();
    private ImportCertificateUseCase useCase;

    @BeforeEach
    void setUp() {
        CertificateChainService chainService = new CertificateChainService(repository);
        useCase = new ImportCertificateUseCase(
                repository,
                pem -> CertificateValidator.ValidationResult.ok(),
                new CertificateDtoMapper(chainService),
                new PermissionChecker(new AppPermissionEvaluator()),
                cryptoPort,
                new CertificateMaterialAssembler(),
                new CertificatePrivateKeyMaterialService(keyManagementPort)
        );
    }

    @Test
    void importCaRestoresCaMetadataAndCrlUrl() {
        cryptoPort.register(descriptor(
                "ca-pem",
                "ca-id",
                "RSA",
                true,
                1,
                "CN=Imported Root, O=SealMail, C=CN",
                "CN=Imported Root, O=SealMail, C=CN",
                "http://localhost:8080/api/v1/crl/imported-root"));

        CertificateResponse response = useCase.execute(ImportCertificateRequest.builder()
                        .ownerEmail("ca@example.com")
                        .pemData("ca-pem")
                        .privateKeyData("matching-key")
                        .trusted(Boolean.TRUE)
                        .build(),
                adminUser());

        assertEquals("http://localhost:8080/api/v1/crl/imported-root", response.getCrlDistributionPointUrl());
        assertEquals(1, response.getPathLenConstraint());
        assertNotNull(response.getId());

        Certificate stored = repository.findById(new CertificateId(response.getId())).orElseThrow();
        assertTrue(stored.isCA());
        assertTrue(stored.hasPrivateKey());
        assertEquals("managed-key:key-ca-id", stored.getPrivateKeySecretRef());
        assertEquals(1, stored.getPathLenConstraint());
        assertEquals("http://localhost:8080/api/v1/crl/imported-root", stored.getCrlDistributionPointUrl());
    }

    @Test
    void importCertificateRejectsMismatchedPrivateKey() {
        cryptoPort.register(descriptor("leaf-pem", "leaf-id", "SM2", false, null,
                "CN=leaf", "CN=leaf", null));
        cryptoPort.rejectPrivateKey = true;

        CertificateException ex = assertThrows(CertificateException.class, () -> useCase.execute(
                ImportCertificateRequest.builder()
                        .ownerEmail("user@example.com")
                        .pemData("leaf-pem")
                        .privateKeyData("wrong-key")
                        .build(),
                adminUser()));

        assertEquals("CERT_INVALID", ex.getCode());
        assertEquals("Private key does not match certificate public key", ex.getMessage());
    }

    @Test
    void importCertificateLinksToStoredIssuerCa() {
        CertificateCryptoPort.CertificateDescriptor rootDescriptor = descriptor(
                "root-pem", "root-id", "RSA", true, 1,
                "CN=Stored Root, O=SealMail, C=CN",
                "CN=Stored Root, O=SealMail, C=CN",
                null);
        cryptoPort.register(rootDescriptor);
        Certificate root = new CertificateMaterialAssembler()
                .issued(rootDescriptor, new EmailAddress("ca@example.com"));
        root.trust();
        repository.save(root);

        cryptoPort.register(descriptor(
                "leaf-pem", "leaf-id", "RSA", false, null,
                "CN=Stored Root, O=SealMail, C=CN",
                "CN=user@example.com",
                null));
        cryptoPort.issuerBySubject.put("leaf-pem", "root-pem");

        CertificateResponse response = useCase.execute(ImportCertificateRequest.builder()
                        .ownerEmail("user@example.com")
                        .pemData("leaf-pem")
                        .trusted(Boolean.TRUE)
                        .build(),
                adminUser());

        assertEquals("root-id", response.getIssuerCertId());
        Certificate stored = repository.findById(new CertificateId(response.getId())).orElseThrow();
        assertEquals("root-id", stored.getIssuerCertId());
        assertTrue(response.isChainUsable());
    }

    private CertificateCryptoPort.CertificateDescriptor descriptor(String pem,
                                                                   String thumbprint,
                                                                   String algorithm,
                                                                   boolean ca,
                                                                   Integer pathLen,
                                                                   String issuerDn,
                                                                   String subjectDn,
                                                                   String crlUrl) {
        return new CertificateCryptoPort.CertificateDescriptor(
                pem,
                algorithm,
                thumbprint,
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                Set.of(KeyUsage.SIGNING, KeyUsage.ENCRYPTION),
                issuerDn,
                subjectDn,
                BigInteger.valueOf(Math.abs(thumbprint.hashCode()) + 1L),
                "ski-" + thumbprint,
                ca,
                pathLen,
                Set.of(),
                crlUrl);
    }

    private UserContext adminUser() {
        return UserContext.builder()
                .userId("admin-1")
                .email("admin@example.com")
                .roles(Set.of("PKI_ADMIN"))
                .build();
    }

    private static final class FakeCertificateCryptoPort implements CertificateCryptoPort {
        private final java.util.Map<String, CertificateDescriptor> descriptors = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, String> issuerBySubject = new java.util.LinkedHashMap<>();
        private boolean rejectPrivateKey;

        void register(CertificateDescriptor descriptor) {
            descriptors.put(descriptor.pemContent(), descriptor);
        }

        @Override
        public CertificateMaterial issueSelfSigned(IssueSelfSignedCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateMaterial issueWithIssuer(IssueWithIssuerCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CryptoCapabilities cryptoCapabilities() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateDescriptor signCsr(SignCsrCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CsrInfo validateCsr(String csrPem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateDescriptor readCertificate(String certificatePem) {
            return descriptors.get(certificatePem);
        }

        @Override
        public void validateCertificateMatchesPrivateKey(String certificatePem, String privateKeyPem) {
            if (rejectPrivateKey) {
                throw new IllegalArgumentException("Private key does not match certificate public key");
            }
        }

        @Override
        public boolean isSelfSigned(String certificatePem) {
            CertificateDescriptor descriptor = descriptors.get(certificatePem);
            return descriptor != null && descriptor.issuerDn().equals(descriptor.subjectDn());
        }

        @Override
        public boolean isIssuedBy(String subjectCertificatePem, String issuerCertificatePem) {
            return issuerCertificatePem.equals(issuerBySubject.get(subjectCertificatePem));
        }

        @Override
        public CrlContent normalizeAndValidateCrl(String caCertificatePem, String crlPem, String crlDerBase64) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CrlContent generateCrl(GenerateCrlCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryCertificateRepository implements CertificateRepository {
        private final java.util.Map<String, Certificate> store = new java.util.LinkedHashMap<>();

        @Override
        public Certificate save(Certificate certificate) {
            store.put(certificate.getId().getThumbprint(), certificate);
            return certificate;
        }

        @Override
        public Optional<Certificate> findById(CertificateId id) {
            return Optional.ofNullable(store.get(id.getThumbprint()));
        }

        @Override
        public List<Certificate> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<Certificate> findByOwner(EmailAddress owner) {
            return store.values().stream()
                    .filter(cert -> cert.getOwner().equals(owner))
                    .toList();
        }

        @Override
        public List<Certificate> findByIssuerCertId(String issuerCertId) {
            return store.values().stream()
                    .filter(cert -> issuerCertId.equals(cert.getIssuerCertId()))
                    .toList();
        }

        @Override
        public List<Certificate> findAllCAs() {
            return store.values().stream().filter(Certificate::isCA).toList();
        }

        @Override
        public List<Certificate> findAllEndEntities() {
            return store.values().stream().filter(cert -> !cert.isCA()).toList();
        }

        @Override
        public List<Certificate> findTrustedForEncryption(EmailAddress owner) {
            return Collections.emptyList();
        }

        @Override
        public List<Certificate> findTrustedForSigning(EmailAddress owner) {
            return Collections.emptyList();
        }

        @Override
        public void deleteById(CertificateId id) {
            store.remove(id.getThumbprint());
        }
    }
}
