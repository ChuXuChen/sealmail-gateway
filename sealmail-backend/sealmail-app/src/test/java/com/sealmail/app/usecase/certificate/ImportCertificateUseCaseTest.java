package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.CertificateException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
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
    private final CertificateCryptoService cryptoService = new CertificateCryptoService();
    private ImportCertificateUseCase useCase;

    @BeforeEach
    void setUp() {
        CertificateChainService chainService = new CertificateChainService(repository);
        useCase = new ImportCertificateUseCase(
                repository,
                pem -> CertificateValidator.ValidationResult.ok(),
                new CertificateDtoMapper(chainService),
                new PermissionChecker(),
                cryptoService
        );
    }

    @Test
    void importCaRestoresCaMetadataAndCrlUrl() throws Exception {
        KeyPair caKeyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate certificate = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(caKeyPair.getPublic())
                .subjectDn("CN=Imported Root, O=SealMail, C=CN")
                .subjectAlgorithm("RSA")
                .issuerDn("CN=Imported Root, O=SealMail, C=CN")
                .issuerPrivKey(caKeyPair.getPrivate())
                .issuerPubKey(caKeyPair.getPublic())
                .validityDays(3650)
                .ca(true)
                .pathLenConstraint(1)
                .crlDpUrl("http://localhost:8080/api/v1/crl/imported-root")
                .build());

        CertificateResponse response = useCase.execute(ImportCertificateRequest.builder()
                        .ownerEmail("ca@example.com")
                        .pemData(cryptoService.toPem(certificate))
                        .privateKeyData(cryptoService.privateKeyToPem(caKeyPair.getPrivate()))
                        .trusted(Boolean.TRUE)
                        .build(),
                adminUser());

        assertEquals("http://localhost:8080/api/v1/crl/imported-root", response.getCrlDistributionPointUrl());
        assertEquals(1, response.getPathLenConstraint());
        assertNotNull(response.getId());

        Certificate stored = repository.findById(new CertificateId(response.getId())).orElseThrow();
        assertTrue(stored.isCA());
        assertEquals(1, stored.getPathLenConstraint());
        assertEquals("http://localhost:8080/api/v1/crl/imported-root", stored.getCrlDistributionPointUrl());
    }

    @Test
    void importCertificateRejectsMismatchedPrivateKey() throws Exception {
        KeyPair certKeyPair = cryptoService.generateKeyPair("SM2");
        KeyPair otherKeyPair = cryptoService.generateKeyPair("SM2");
        X509Certificate certificate = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(certKeyPair.getPublic())
                .subjectDn("CN=user@example.com")
                .subjectAlgorithm("SM2")
                .issuerDn("CN=user@example.com")
                .issuerPrivKey(certKeyPair.getPrivate())
                .issuerPubKey(certKeyPair.getPublic())
                .validityDays(365)
                .ca(false)
                .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                .build());

        CertificateException ex = assertThrows(CertificateException.class, () -> useCase.execute(
                ImportCertificateRequest.builder()
                        .ownerEmail("user@example.com")
                        .pemData(cryptoService.toPem(certificate))
                        .privateKeyData(cryptoService.privateKeyToPem(otherKeyPair.getPrivate()))
                        .build(),
                adminUser()));

        assertEquals("CERT_INVALID", ex.getCode());
        assertEquals("Private key does not match certificate public key", ex.getMessage());
    }

    @Test
    void importCertificateLinksToStoredIssuerCa() throws Exception {
        KeyPair rootKeyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate rootX509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(rootKeyPair.getPublic())
                .subjectDn("CN=Stored Root, O=SealMail, C=CN")
                .subjectAlgorithm("RSA")
                .issuerDn("CN=Stored Root, O=SealMail, C=CN")
                .issuerPrivKey(rootKeyPair.getPrivate())
                .issuerPubKey(rootKeyPair.getPublic())
                .validityDays(3650)
                .ca(true)
                .pathLenConstraint(1)
                .build());
        CertificateId rootId = new CertificateId(cryptoService.computeThumbprint(rootX509));
        Certificate root = cryptoService.toIssuedDomainCertificate(
                rootId,
                new com.sealmail.domain.shared.model.EmailAddress("ca@example.com"),
                rootX509,
                "RSA");
        root.markAsCA(1);
        root.trust();
        repository.save(root);

        KeyPair leafKeyPair = cryptoService.generateKeyPair("RSA");
        X509Certificate leafX509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                .subjectPubKey(leafKeyPair.getPublic())
                .subjectDn("CN=user@example.com")
                .subjectAlgorithm("RSA")
                .issuerDn(rootX509.getSubjectX500Principal().getName())
                .issuerPrivKey(rootKeyPair.getPrivate())
                .issuerPubKey(rootX509.getPublicKey())
                .validityDays(365)
                .ca(false)
                .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                .build());

        CertificateResponse response = useCase.execute(ImportCertificateRequest.builder()
                        .ownerEmail("user@example.com")
                        .pemData(cryptoService.toPem(leafX509))
                        .trusted(Boolean.TRUE)
                        .build(),
                adminUser());

        assertEquals(rootId.getThumbprint(), response.getIssuerCertId());
        Certificate stored = repository.findById(new CertificateId(response.getId())).orElseThrow();
        assertEquals(rootId.getThumbprint(), stored.getIssuerCertId());
        assertTrue(response.isChainUsable());
    }

    private UserContext adminUser() {
        return UserContext.builder()
                .userId("admin-1")
                .email("admin@example.com")
                .roles(Set.of("PKI_ADMIN"))
                .build();
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
        public List<Certificate> findByOwner(com.sealmail.domain.shared.model.EmailAddress owner) {
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
        public List<Certificate> findTrustedForEncryption(com.sealmail.domain.shared.model.EmailAddress owner) {
            return Collections.emptyList();
        }

        @Override
        public List<Certificate> findTrustedForSigning(com.sealmail.domain.shared.model.EmailAddress owner) {
            return Collections.emptyList();
        }

        @Override
        public void deleteById(CertificateId id) {
            store.remove(id.getThumbprint());
        }
    }
}
