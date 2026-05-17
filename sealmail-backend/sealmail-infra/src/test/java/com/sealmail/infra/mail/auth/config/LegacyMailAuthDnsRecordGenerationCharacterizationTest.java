package com.sealmail.infra.mail.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.persistence.entity.MailAuthConfigEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyMailAuthDnsRecordGenerationCharacterizationTest {

    @Test
    void generatesCurrentSpfAndDmarcRecordsFromGlobalConfig() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(MailAuthConfigEntity.class, "default")).thenReturn(entity());
        MailAuthConfigService service = service(entityManager);

        List<DnsRecordResponse> records = service.dnsRecords("Example.COM.");

        assertEquals(3, records.size());
        assertEquals(new DnsRecordResponse("DKIM", "sealmail._domainkey.example.com", "", false), records.get(0));
        assertEquals(new DnsRecordResponse("SPF", "example.com",
                "v=spf1 a mx ip4:203.0.113.10 include:sender.example.net ~all", true), records.get(1));
        assertEquals(new DnsRecordResponse("DMARC", "_dmarc.example.com",
                "v=DMARC1; p=none; adkim=r; aspf=s; pct=25; rua=mailto:dmarc@example.com", true), records.get(2));
    }

    @Test
    void disabledPublicationRecordsAreUnavailable() {
        MailAuthConfigEntity entity = entity();
        entity.setSpfEnabled(false);
        entity.setDmarcEnabled(false);
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(MailAuthConfigEntity.class, "default")).thenReturn(entity);
        MailAuthConfigService service = service(entityManager);

        List<DnsRecordResponse> records = service.dnsRecords("example.com");

        assertFalse(records.get(1).available());
        assertEquals("", records.get(1).value());
        assertFalse(records.get(2).available());
        assertEquals("", records.get(2).value());
    }

    private static MailAuthConfigService service(EntityManager entityManager) {
        MailAuthConfigService service = new MailAuthConfigService(
                new ObjectMapper(),
                unresolvedSecrets());
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    private static MailAuthConfigEntity entity() {
        MailAuthConfigEntity entity = new MailAuthConfigEntity();
        entity.setId("default");
        entity.setEnabled(true);
        entity.setAuthservId("sealmail-gateway");
        entity.setSkipPrivateRelay(true);
        entity.setDkimEnabled(true);
        entity.setDkimSelector("sealmail");
        entity.setDkimPrivateKeyPath(null);
        entity.setDkimPrivateKeySecretRef(null);
        entity.setDkimSignedHeaders("[\"from\",\"to\",\"subject\"]");
        entity.setSpfEnabled(true);
        entity.setSpfMaxDnsLookups(10);
        entity.setSpfUseA(true);
        entity.setSpfUseMx(true);
        entity.setSpfIp4("[\"203.0.113.10\"]");
        entity.setSpfIp6("[]");
        entity.setSpfIncludes("[\"sender.example.net\"]");
        entity.setSpfAllPolicy("~all");
        entity.setDmarcEnabled(true);
        entity.setDmarcPolicy("none");
        entity.setDmarcAdkim("r");
        entity.setDmarcAspf("s");
        entity.setDmarcPct(25);
        entity.setDmarcRua("mailto:dmarc@example.com");
        entity.setDmarcRuf(null);
        entity.setDmarcFailureAction("LOG_ONLY");
        entity.setDmarcQuarantineRejectPolicy(false);
        entity.setCreatedAt(Instant.parse("2025-04-01T12:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2025-04-01T12:00:00Z"));
        return entity;
    }

    private static SecretReferenceResolver unresolvedSecrets() {
        return secretRef -> {
            throw new IllegalArgumentException("Unexpected secret ref in characterization test: " + secretRef);
        };
    }
}
