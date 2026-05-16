package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.SubmitCertRequestRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.domain.certificate.CertificateRequest;
import com.sealmail.domain.certificate.CertificateRequestRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateRequestUseCaseTest {

    private final FakeCertificateCryptoPort cryptoPort = new FakeCertificateCryptoPort();
    private final InMemoryCertificateRequestRepository repository = new InMemoryCertificateRequestRepository();
    private final CertificateRequestUseCase useCase = new CertificateRequestUseCase(
            repository,
            null,
            cryptoPort,
            new PermissionChecker());

    @Test
    void submitRejectsRequestedOwnerThatDiffersFromCsrSubjectEmail() {
        cryptoPort.ownerEmail = "csr-owner@example.com";

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.submit(
                SubmitCertRequestRequest.builder()
                        .csrPem("csr-pem")
                        .requestedOwnerEmail("different@example.com")
                        .build(),
                "127.0.0.1"));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("申请邮箱与 CSR Subject 中的邮箱不一致"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void submitUsesCsrSubjectEmailWhenHintIsAbsent() {
        cryptoPort.ownerEmail = "csr-owner@example.com";

        var response = useCase.submit(
                SubmitCertRequestRequest.builder()
                        .csrPem("csr-pem")
                        .build(),
                "127.0.0.1");

        assertEquals("csr-owner@example.com", response.getRequestedOwnerEmail());
        assertEquals("PENDING", response.getStatus());
    }

    private static final class FakeCertificateCryptoPort implements CertificateCryptoPort {
        private String ownerEmail;

        @Override
        public CertificateMaterial issueSelfSigned(IssueSelfSignedCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateMaterial issueWithIssuer(IssueWithIssuerCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CertificateMaterial generateTestMaterial() {
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
            return new CsrInfo("CN=" + ownerEmail, ownerEmail, "RSA");
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

    private static final class InMemoryCertificateRequestRepository implements CertificateRequestRepository {
        private final java.util.Map<String, CertificateRequest> store = new java.util.LinkedHashMap<>();

        @Override
        public CertificateRequest save(CertificateRequest request) {
            store.put(request.getId(), request);
            return request;
        }

        @Override
        public Optional<CertificateRequest> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<CertificateRequest> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<CertificateRequest> findByStatus(CertificateRequest.Status status) {
            return store.values().stream()
                    .filter(request -> request.getStatus() == status)
                    .toList();
        }

        @Override
        public void deleteById(String id) {
            store.remove(id);
        }
    }
}
