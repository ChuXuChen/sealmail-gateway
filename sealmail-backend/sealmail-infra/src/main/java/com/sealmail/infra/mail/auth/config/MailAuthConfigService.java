package com.sealmail.infra.mail.auth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.persistence.entity.MailAuthConfigEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy helper kept for characterization of the old mail_auth_config DNS output.
 * Production configuration writes now use MailAuthPolicyRepository and domain/mailauth models.
 */
public class MailAuthConfigService {

    private static final String DEFAULT_ID = "default";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;
    private final SecretReferenceResolver secretReferenceResolver;

    public MailAuthConfigService(ObjectMapper objectMapper,
                                 SecretReferenceResolver secretReferenceResolver) {
        this.objectMapper = objectMapper;
        this.secretReferenceResolver = secretReferenceResolver;
    }

    @Transactional(readOnly = true)
    public List<DnsRecordResponse> dnsRecords(String domain) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthConfigEntity entity = entityManager.find(MailAuthConfigEntity.class, DEFAULT_ID);
        if (entity == null) {
            return List.of(
                    new DnsRecordResponse("DKIM", "sealmail._domainkey." + normalizedDomain, "", false),
                    new DnsRecordResponse("SPF", normalizedDomain, "", false),
                    new DnsRecordResponse("DMARC", "_dmarc." + normalizedDomain, "", false));
        }
        return List.of(
                dkimRecord(entity, normalizedDomain),
                spfRecord(entity, normalizedDomain),
                dmarcRecord(entity, normalizedDomain));
    }

    private DnsRecordResponse dkimRecord(MailAuthConfigEntity entity, String domain) {
        String name = entity.getDkimSelector() + "._domainkey." + domain;
        String key = publicKeyData(entity);
        if (!entity.isDkimEnabled() || !hasText(key)) {
            return new DnsRecordResponse("DKIM", name, "", false);
        }
        return new DnsRecordResponse("DKIM", name, "v=DKIM1; k=rsa; p=" + key, true);
    }

    private DnsRecordResponse spfRecord(MailAuthConfigEntity entity, String domain) {
        if (!entity.isSpfEnabled()) {
            return new DnsRecordResponse("SPF", domain, "", false);
        }
        List<String> parts = new ArrayList<>();
        parts.add("v=spf1");
        if (entity.isSpfUseA()) {
            parts.add("a");
        }
        if (entity.isSpfUseMx()) {
            parts.add("mx");
        }
        readList(entity.getSpfIp4()).forEach(value -> parts.add("ip4:" + value));
        readList(entity.getSpfIp6()).forEach(value -> parts.add("ip6:" + value));
        readList(entity.getSpfIncludes()).forEach(value -> parts.add("include:" + value));
        parts.add(defaultText(entity.getSpfAllPolicy(), "~all"));
        return new DnsRecordResponse("SPF", domain, String.join(" ", parts), true);
    }

    private DnsRecordResponse dmarcRecord(MailAuthConfigEntity entity, String domain) {
        String name = "_dmarc." + domain;
        if (!entity.isDmarcEnabled()) {
            return new DnsRecordResponse("DMARC", name, "", false);
        }
        List<String> parts = new ArrayList<>();
        parts.add("v=DMARC1");
        parts.add("p=" + entity.getDmarcPolicy());
        parts.add("adkim=" + entity.getDmarcAdkim());
        parts.add("aspf=" + entity.getDmarcAspf());
        parts.add("pct=" + entity.getDmarcPct());
        if (hasText(entity.getDmarcRua())) {
            parts.add("rua=" + entity.getDmarcRua().trim());
        }
        if (hasText(entity.getDmarcRuf())) {
            parts.add("ruf=" + entity.getDmarcRuf().trim());
        }
        return new DnsRecordResponse("DMARC", name, String.join("; ", parts), true);
    }

    private String publicKeyData(MailAuthConfigEntity entity) {
        String keyPem = resolvePrivateKeyPem(entity);
        if (!hasText(keyPem)) {
            return null;
        }
        try {
            PrivateKey privateKey = PemUtils.parsePrivateKey(keyPem, null);
            if (!(privateKey instanceof RSAPrivateCrtKey rsa)) {
                return null;
            }
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
            byte[] der = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).getEncoded();
            return java.util.Base64.getEncoder().encodeToString(der);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolvePrivateKeyPem(MailAuthConfigEntity entity) {
        if (hasText(entity.getDkimPrivateKeyPath())) {
            try {
                return Files.readString(Path.of(entity.getDkimPrivateKeyPath()));
            } catch (Exception ignored) {
                return null;
            }
        }
        if (hasText(entity.getDkimPrivateKeySecretRef())) {
            try {
                return secretReferenceResolver.resolve(entity.getDkimPrivateKeySecretRef());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> readList(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
