package com.sealmail.infra.mail.auth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.domain.policy.event.MailAuthConfigChanged;
import com.sealmail.infra.config.properties.MailAuthProperties;
import com.sealmail.infra.crypto.util.PemUtils;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.MailAuthConfigEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MailAuthConfigService {

    private static final String DEFAULT_ID = "default";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final Set<String> ALL_POLICIES = Set.of("-all", "~all", "?all");
    private static final Set<String> DMARC_POLICIES = Set.of("none", "quarantine", "reject");
    private static final Set<String> ALIGNMENT_MODES = Set.of("r", "s");
    private static final Set<String> FAILURE_ACTIONS = Set.of("LOG_ONLY", "APPLY_POLICY");
    private static final List<String> DEFAULT_SIGNED_HEADERS = List.of("from", "to", "subject", "date", "message-id");

    @PersistenceContext
    private EntityManager entityManager;

    private final MailAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher domainEventPublisher;

    public MailAuthConfigService(MailAuthProperties properties,
                                 ObjectMapper objectMapper,
                                 DomainEventPublisher domainEventPublisher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        applyToProperties(entity());
    }

    @Transactional
    public MailAuthConfig getConfig() {
        return toResponse(entity());
    }

    @Transactional
    public MailAuthConfig updateConfig(MailAuthConfigUpdate update) {
        MailAuthConfigEntity entity = entity();
        applyUpdate(entity, update);
        entity.setUpdatedAt(Instant.now());
        MailAuthConfigEntity saved = entityManager.merge(entity);
        applyToProperties(saved);
        domainEventPublisher.publishEvent(new MailAuthConfigChanged(
                DEFAULT_ID,
                changedSections(update)));
        return toResponse(saved);
    }

    @Transactional
    public List<DnsRecordResponse> dnsRecords(String domain) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthConfigEntity entity = entity();
        return List.of(
                dkimRecord(entity, normalizedDomain),
                spfRecord(entity, normalizedDomain),
                dmarcRecord(entity, normalizedDomain)
        );
    }

    private MailAuthConfigEntity entity() {
        MailAuthConfigEntity entity = entityManager.find(MailAuthConfigEntity.class, DEFAULT_ID);
        if (entity != null) {
            return entity;
        }
        MailAuthConfigEntity created = defaultEntity();
        entityManager.persist(created);
        return created;
    }

    private MailAuthConfigEntity defaultEntity() {
        Instant now = Instant.now();
        MailAuthConfigEntity entity = new MailAuthConfigEntity();
        entity.setId(DEFAULT_ID);
        entity.setEnabled(properties.isEnabled());
        entity.setAuthservId(defaultText(properties.getAuthservId(), "sealmail-gateway"));
        entity.setSkipPrivateRelay(properties.isSkipPrivateRelay());
        entity.setDkimEnabled(properties.getDkim().isEnabled());
        entity.setDkimSelector(defaultText(properties.getDkim().getSelector(), "sealmail"));
        entity.setDkimPrivateKeyPath(blankToNull(properties.getDkim().getPrivateKeyPath()));
        entity.setDkimPrivateKeyPem(blankToNull(properties.getDkim().getPrivateKeyPem()));
        entity.setDkimSignedHeaders(writeList(defaultList(properties.getDkim().getSignedHeaders(), DEFAULT_SIGNED_HEADERS)));
        entity.setSpfEnabled(properties.getSpf().isEnabled());
        entity.setSpfMaxDnsLookups(properties.getSpf().getMaxDnsLookups());
        entity.setSpfUseA(true);
        entity.setSpfUseMx(true);
        entity.setSpfIp4(writeList(List.of()));
        entity.setSpfIp6(writeList(List.of()));
        entity.setSpfIncludes(writeList(List.of()));
        entity.setSpfAllPolicy("~all");
        entity.setDmarcEnabled(properties.getDmarc().isEnabled());
        entity.setDmarcPolicy("quarantine");
        entity.setDmarcAdkim("r");
        entity.setDmarcAspf("r");
        entity.setDmarcPct(100);
        entity.setDmarcRua(null);
        entity.setDmarcRuf(null);
        entity.setDmarcFailureAction(properties.getDmarc().isQuarantineRejectPolicy() ? "APPLY_POLICY" : "LOG_ONLY");
        entity.setDmarcQuarantineRejectPolicy(properties.getDmarc().isQuarantineRejectPolicy());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyUpdate(MailAuthConfigEntity entity, MailAuthConfigUpdate update) {
        if (update == null) {
            return;
        }
        if (update.enabled() != null) {
            entity.setEnabled(update.enabled());
        }
        if (update.authservId() != null) {
            String value = update.authservId().trim();
            require(hasText(value), "认证服务标识不能为空");
            entity.setAuthservId(value);
        }
        if (update.skipPrivateRelay() != null) {
            entity.setSkipPrivateRelay(update.skipPrivateRelay());
        }
        if (update.dkimEnabled() != null) {
            entity.setDkimEnabled(update.dkimEnabled());
        }
        if (update.dkimSelector() != null) {
            String value = update.dkimSelector().trim();
            require(value.matches("[A-Za-z0-9._-]{1,128}"), "DKIM selector 格式无效");
            entity.setDkimSelector(value);
        }
        if (update.dkimPrivateKeyPath() != null) {
            entity.setDkimPrivateKeyPath(blankToNull(update.dkimPrivateKeyPath()));
        }
        if (Boolean.TRUE.equals(update.clearDkimPrivateKeyPem())) {
            entity.setDkimPrivateKeyPem(null);
        } else if (update.dkimPrivateKeyPem() != null && !update.dkimPrivateKeyPem().isBlank()) {
            validatePrivateKey(update.dkimPrivateKeyPem());
            entity.setDkimPrivateKeyPem(update.dkimPrivateKeyPem().trim());
        }
        if (update.dkimSignedHeaders() != null) {
            List<String> headers = normalizeHeaders(update.dkimSignedHeaders());
            require(!headers.isEmpty(), "DKIM 签名头不能为空");
            entity.setDkimSignedHeaders(writeList(headers));
        }
        if (update.spfEnabled() != null) {
            entity.setSpfEnabled(update.spfEnabled());
        }
        if (update.spfMaxDnsLookups() != null) {
            require(update.spfMaxDnsLookups() >= 0 && update.spfMaxDnsLookups() <= 50, "SPF DNS 查询次数必须在 0 到 50 之间");
            entity.setSpfMaxDnsLookups(update.spfMaxDnsLookups());
        }
        if (update.spfUseA() != null) {
            entity.setSpfUseA(update.spfUseA());
        }
        if (update.spfUseMx() != null) {
            entity.setSpfUseMx(update.spfUseMx());
        }
        if (update.spfIp4() != null) {
            entity.setSpfIp4(writeList(cleanList(update.spfIp4())));
        }
        if (update.spfIp6() != null) {
            entity.setSpfIp6(writeList(cleanList(update.spfIp6())));
        }
        if (update.spfIncludes() != null) {
            entity.setSpfIncludes(writeList(normalizeDomains(update.spfIncludes())));
        }
        if (update.spfAllPolicy() != null) {
            String value = update.spfAllPolicy().trim().toLowerCase(Locale.ROOT);
            require(ALL_POLICIES.contains(value), "SPF all 策略无效");
            entity.setSpfAllPolicy(value);
        }
        if (update.dmarcEnabled() != null) {
            entity.setDmarcEnabled(update.dmarcEnabled());
        }
        if (update.dmarcPolicy() != null) {
            String value = update.dmarcPolicy().trim().toLowerCase(Locale.ROOT);
            require(DMARC_POLICIES.contains(value), "DMARC 策略无效");
            entity.setDmarcPolicy(value);
        }
        if (update.dmarcAdkim() != null) {
            entity.setDmarcAdkim(validateAlignment(update.dmarcAdkim(), "DKIM 对齐模式无效"));
        }
        if (update.dmarcAspf() != null) {
            entity.setDmarcAspf(validateAlignment(update.dmarcAspf(), "SPF 对齐模式无效"));
        }
        if (update.dmarcPct() != null) {
            require(update.dmarcPct() >= 0 && update.dmarcPct() <= 100, "DMARC 生效比例必须在 0 到 100 之间");
            entity.setDmarcPct(update.dmarcPct());
        }
        if (update.dmarcRua() != null) {
            entity.setDmarcRua(blankToNull(update.dmarcRua()));
        }
        if (update.dmarcRuf() != null) {
            entity.setDmarcRuf(blankToNull(update.dmarcRuf()));
        }
        if (update.dmarcFailureAction() != null) {
            String value = update.dmarcFailureAction().trim().toUpperCase(Locale.ROOT);
            require(FAILURE_ACTIONS.contains(value), "DMARC 失败处理无效");
            entity.setDmarcFailureAction(value);
            entity.setDmarcQuarantineRejectPolicy(!"LOG_ONLY".equals(value));
        }
        if (update.dmarcQuarantineRejectPolicy() != null) {
            entity.setDmarcQuarantineRejectPolicy(update.dmarcQuarantineRejectPolicy());
            entity.setDmarcFailureAction(update.dmarcQuarantineRejectPolicy() ? "APPLY_POLICY" : "LOG_ONLY");
        }
    }

    private MailAuthConfig toResponse(MailAuthConfigEntity entity) {
        return new MailAuthConfig(
                entity.isEnabled(),
                entity.getAuthservId(),
                entity.isSkipPrivateRelay(),
                entity.isDkimEnabled(),
                entity.getDkimSelector(),
                entity.getDkimPrivateKeyPath(),
                hasText(entity.getDkimPrivateKeyPath()) || hasText(entity.getDkimPrivateKeyPem()),
                readList(entity.getDkimSignedHeaders()),
                entity.isSpfEnabled(),
                entity.getSpfMaxDnsLookups(),
                entity.isSpfUseA(),
                entity.isSpfUseMx(),
                readList(entity.getSpfIp4()),
                readList(entity.getSpfIp6()),
                readList(entity.getSpfIncludes()),
                entity.getSpfAllPolicy(),
                entity.isDmarcEnabled(),
                entity.getDmarcPolicy(),
                entity.getDmarcAdkim(),
                entity.getDmarcAspf(),
                entity.getDmarcPct(),
                entity.getDmarcRua(),
                entity.getDmarcRuf(),
                entity.getDmarcFailureAction(),
                entity.isDmarcQuarantineRejectPolicy(),
                entity.getUpdatedAt()
        );
    }

    private void applyToProperties(MailAuthConfigEntity entity) {
        properties.setEnabled(entity.isEnabled());
        properties.setAuthservId(entity.getAuthservId());
        properties.setSkipPrivateRelay(entity.isSkipPrivateRelay());
        properties.getDkim().setEnabled(entity.isDkimEnabled());
        properties.getDkim().setSelector(entity.getDkimSelector());
        properties.getDkim().setPrivateKeyPath(entity.getDkimPrivateKeyPath());
        properties.getDkim().setPrivateKeyPem(entity.getDkimPrivateKeyPem());
        properties.getDkim().setSignedHeaders(readList(entity.getDkimSignedHeaders()));
        properties.getSpf().setEnabled(entity.isSpfEnabled());
        properties.getSpf().setMaxDnsLookups(entity.getSpfMaxDnsLookups());
        properties.getDmarc().setEnabled(entity.isDmarcEnabled());
        properties.getDmarc().setQuarantineRejectPolicy(entity.isDmarcQuarantineRejectPolicy());
    }

    private List<String> changedSections(MailAuthConfigUpdate update) {
        if (update == null) {
            return List.of("GENERAL");
        }
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        if (update.enabled() != null || update.authservId() != null || update.skipPrivateRelay() != null) {
            sections.add("GENERAL");
        }
        if (update.dkimEnabled() != null
                || update.dkimSelector() != null
                || update.dkimPrivateKeyPath() != null
                || update.dkimPrivateKeyPem() != null
                || update.clearDkimPrivateKeyPem() != null
                || update.dkimSignedHeaders() != null) {
            sections.add("DKIM");
        }
        if (update.spfEnabled() != null
                || update.spfMaxDnsLookups() != null
                || update.spfUseA() != null
                || update.spfUseMx() != null
                || update.spfIp4() != null
                || update.spfIp6() != null
                || update.spfIncludes() != null
                || update.spfAllPolicy() != null) {
            sections.add("SPF");
        }
        if (update.dmarcEnabled() != null
                || update.dmarcPolicy() != null
                || update.dmarcAdkim() != null
                || update.dmarcAspf() != null
                || update.dmarcPct() != null
                || update.dmarcRua() != null
                || update.dmarcRuf() != null
                || update.dmarcFailureAction() != null
                || update.dmarcQuarantineRejectPolicy() != null) {
            sections.add("DMARC");
        }
        return sections.isEmpty() ? List.of("GENERAL") : List.copyOf(sections);
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
        if (hasText(entity.getDkimPrivateKeyPem())) {
            return entity.getDkimPrivateKeyPem();
        }
        if (hasText(entity.getDkimPrivateKeyPath())) {
            try {
                return Files.readString(Path.of(entity.getDkimPrivateKeyPath()));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private void validatePrivateKey(String keyPem) {
        try {
            PemUtils.parsePrivateKey(keyPem, null);
        } catch (Exception e) {
            throw new IllegalArgumentException("DKIM 私钥格式无效");
        }
    }

    private String validateAlignment(String value, String message) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        require(ALIGNMENT_MODES.contains(normalized), message);
        return normalized;
    }

    private List<String> normalizeHeaders(List<String> values) {
        return values.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> normalizeDomains(List<String> values) {
        return cleanList(values).stream()
                .map(DomainName::requireValid)
                .toList();
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return List.copyOf(cleaned);
    }

    private List<String> defaultList(List<String> values, List<String> fallback) {
        List<String> cleaned = cleanList(values);
        return cleaned.isEmpty() ? fallback : cleaned;
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

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values != null ? values : List.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("列表序列化失败");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
