package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.certificate.CertificateRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CertificateRequestRepositoryImplTest {

    @Test
    void toDomainPreservesSubmittedAt() throws Exception {
        CertificateRequestRepositoryImpl repository = new CertificateRequestRepositoryImpl(mock(EntityManager.class));
        com.sealmail.infra.persistence.entity.CertificateRequestEntity entity =
                new com.sealmail.infra.persistence.entity.CertificateRequestEntity();
        Instant submittedAt = Instant.parse("2026-05-14T12:34:56Z");

        entity.setId("req-1");
        entity.setCsrPem("-----BEGIN CERTIFICATE REQUEST-----\nMIIBVTCB/wIBADAaMRgwFgYDVQQDDA91c2VyQGV4YW1wbGUuY29tMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBANQxj2tGe7Y0v2t7fT9XqGmX3mS+4fX7L2dBI6mR7sM0hKcNw2rWzQJY2t7m8bJ0rEu0+0xq8T2u4uZxEoOGGx2L43kCAwEAAaAAMA0GCSqGSIb3DQEBCwUAA0EAKQyK2yLhJc2q5BDrgQ8QXfJfJ9A9V7P2g4Xn2EmjYJcB4W2B6Knx5oJf4dzVQm4Xn4oGgYhXr5c2U0VQjH1JZA==\n-----END CERTIFICATE REQUEST-----");
        entity.setRequestedOwnerEmail("user@example.com");
        entity.setSubmittedAt(submittedAt);
        entity.setStatus(CertificateRequest.Status.PENDING.name());

        Method method = CertificateRequestRepositoryImpl.class.getDeclaredMethod("toDomain",
                com.sealmail.infra.persistence.entity.CertificateRequestEntity.class);
        method.setAccessible(true);
        CertificateRequest request = (CertificateRequest) method.invoke(repository, entity);

        assertEquals(submittedAt, request.getSubmittedAt());
        assertEquals(CertificateRequest.Status.PENDING, request.getStatus());
    }
}
