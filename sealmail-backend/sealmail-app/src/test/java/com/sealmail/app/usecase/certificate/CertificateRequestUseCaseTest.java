package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.SubmitCertRequestRequest;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.domain.certificate.CertificateRequest;
import com.sealmail.domain.certificate.CertificateRequestRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateRequestUseCaseTest {

    private final CertificateCryptoService cryptoService = new CertificateCryptoService();
    private final InMemoryCertificateRequestRepository repository = new InMemoryCertificateRequestRepository();
    private final CertificateRequestUseCase useCase = new CertificateRequestUseCase(
            repository,
            null,
            cryptoService,
            new PermissionChecker());

    @Test
    void submitRejectsRequestedOwnerThatDiffersFromCsrSubjectEmail() throws Exception {
        String csrPem = buildCsrPem("CN=csr-owner@example.com");

        BusinessException ex = assertThrows(BusinessException.class, () -> useCase.submit(
                SubmitCertRequestRequest.builder()
                        .csrPem(csrPem)
                        .requestedOwnerEmail("different@example.com")
                        .build(),
                "127.0.0.1"));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("申请邮箱与 CSR Subject 中的邮箱不一致"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void submitUsesCsrSubjectEmailWhenHintIsAbsent() throws Exception {
        String csrPem = buildCsrPem("CN=csr-owner@example.com");

        var response = useCase.submit(
                SubmitCertRequestRequest.builder()
                        .csrPem(csrPem)
                        .build(),
                "127.0.0.1");

        assertEquals("csr-owner@example.com", response.getRequestedOwnerEmail());
        assertEquals("PENDING", response.getStatus());
    }

    private String buildCsrPem(String subjectDn) throws Exception {
        KeyPair keyPair = cryptoService.generateKeyPair("RSA");
        PKCS10CertificationRequestBuilder builder =
                new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn), keyPair.getPublic());
        PKCS10CertificationRequest csr = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.getPrivate()));
        StringWriter sw = new StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter writer =
                     new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            writer.writeObject(csr);
        }
        return sw.toString();
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
