package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.CreateIntermediateCaRequest;
import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.request.SignCsrRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateIssuanceAlgorithmPolicyTest {

    private final InMemoryCertificateRepository repository = new InMemoryCertificateRepository();
    private final CertificateChainService chainService = new CertificateChainService(repository);
    private final CertificateDtoMapper mapper = new CertificateDtoMapper(chainService);
    private final FakeCertificateCryptoPort cryptoPort = new FakeCertificateCryptoPort();
    private final FakeKeyManagementPort keyManagementPort = new FakeKeyManagementPort();
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService =
            new CertificatePrivateKeyMaterialService(keyManagementPort);
    private final CertificateAlgorithmPolicy algorithmPolicy = new CertificateAlgorithmPolicy();
    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final CertificateMaterialAssembler materialAssembler = new CertificateMaterialAssembler();

    @Test
    void createIntermediateRejectsAlgorithmDifferentFromRootCa() {
        Certificate root = ca("sm2-root", "SM2", 1);
        repository.save(root);
        keyManagementPort.importCertificateKey(root.getOwner(), root.getAlgorithm(), KeyPurpose.CA_SIGNING,
                root.getId().getThumbprint(), root.getPemContent(), "test-private-key");
        CreateIntermediateCaUseCase useCase = new CreateIntermediateCaUseCase(
                repository,
                mapper,
                materialAssembler,
                privateKeyMaterialService,
                keyManagementPort,
                permissionChecker,
                chainService,
                algorithmPolicy,
                1825);

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(
                CreateIntermediateCaRequest.builder()
                        .rootCaId(root.getId().getThumbprint())
                        .commonName("RSA Intermediate")
                        .algorithm("RSA")
                        .build(),
                admin()));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("RSA 不能由 SM2 签发"));
        assertEquals(0, cryptoPort.issueWithIssuerCalls);
    }

    @Test
    void issueEndEntityRejectsAlgorithmDifferentFromIntermediateCa() {
        Certificate intermediate = ca("sm2-intermediate", "SM2", 0);
        repository.save(intermediate);
        keyManagementPort.importCertificateKey(intermediate.getOwner(), intermediate.getAlgorithm(), KeyPurpose.CA_SIGNING,
                intermediate.getId().getThumbprint(), intermediate.getPemContent(), "test-private-key");
        IssueEndEntityUseCase useCase = new IssueEndEntityUseCase(
                repository,
                mapper,
                permissionChecker,
                materialAssembler,
                privateKeyMaterialService,
                keyManagementPort,
                chainService,
                algorithmPolicy,
                "http://localhost:8080/api/v1/crl/",
                365);

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(
                IssueEndEntityRequest.builder()
                        .intermediateCaId(intermediate.getId().getThumbprint())
                        .ownerEmail("user@example.com")
                        .algorithm("RSA")
                        .build(),
                admin()));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("RSA 不能由 SM2 签发"));
        assertEquals(0, cryptoPort.issueWithIssuerCalls);
    }

    @Test
    void signCsrRejectsAlgorithmDifferentFromIntermediateCa() {
        Certificate intermediate = ca("sm2-intermediate", "SM2", 0);
        repository.save(intermediate);
        keyManagementPort.importCertificateKey(intermediate.getOwner(), intermediate.getAlgorithm(), KeyPurpose.CA_SIGNING,
                intermediate.getId().getThumbprint(), intermediate.getPemContent(), "test-private-key");
        cryptoPort.csrAlgorithm = "RSA";
        SignCsrUseCase useCase = new SignCsrUseCase(
                repository,
                mapper,
                permissionChecker,
                cryptoPort,
                materialAssembler,
                privateKeyMaterialService,
                keyManagementPort,
                chainService,
                algorithmPolicy,
                "http://localhost:8080/api/v1/crl/");

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.execute(
                SignCsrRequest.builder()
                        .caCertId(intermediate.getId().getThumbprint())
                        .csrPem("csr-pem")
                        .build(),
                admin()));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("RSA 不能由 SM2 签发"));
        assertEquals(0, cryptoPort.signCsrCalls);
    }

    private Certificate ca(String id, String algorithm, int pathLen) {
        Certificate cert = Certificate.issueCertificate(
                new CertificateId(id),
                new EmailAddress("ca-" + id + "@sealmail.local"),
                "pem-" + id,
                new ValidityPeriod(Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                Set.of(KeyUsage.SIGNING, KeyUsage.ENCRYPTION),
                "CN=" + id,
                "CN=" + id,
                BigInteger.valueOf(Math.abs(id.hashCode()) + 1L),
                "ski-" + id);
        cert.setAlgorithm(algorithm);
        cert.markAsCA(pathLen);
        cert.setPrivateKeySecretRef("test-secret:" + id);
        cert.trust();
        cert.clearDomainEvents();
        return cert;
    }

    private UserContext admin() {
        return UserContext.builder()
                .userId("admin-1")
                .email("admin@example.com")
                .roles(Set.of("PKI_ADMIN"))
                .build();
    }

    private static final class FakeCertificateCryptoPort implements CertificateCryptoPort {
        private int issueWithIssuerCalls;
        private int signCsrCalls;
        private String csrAlgorithm = "RSA";

        @Override
        public CertificateMaterial issueSelfSigned(IssueSelfSignedCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateMaterial issueWithIssuer(IssueWithIssuerCommand command) {
            issueWithIssuerCalls++;
            throw new UnsupportedOperationException("issueWithIssuer should not be called");
        }

        @Override
        public CryptoCapabilities cryptoCapabilities() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateDescriptor signCsr(SignCsrCommand command) {
            signCsrCalls++;
            throw new UnsupportedOperationException("signCsr should not be called");
        }

        @Override
        public CsrInfo validateCsr(String csrPem) {
            return new CsrInfo("CN=user@example.com", "user@example.com", csrAlgorithm);
        }

        @Override
        public CertificateDescriptor readCertificate(String certificatePem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateCertificateMatchesPrivateKey(String certificatePem, String privateKeyPem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSelfSigned(String certificatePem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIssuedBy(String subjectCertificatePem, String issuerCertificatePem) {
            throw new UnsupportedOperationException();
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
