package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimSelector;
import com.sealmail.domain.mailauth.DkimSigningPolicy;
import com.sealmail.domain.mailauth.DmarcAlignmentMode;
import com.sealmail.domain.mailauth.DmarcPolicyMode;
import com.sealmail.domain.mailauth.DmarcPublicationPolicy;
import com.sealmail.domain.mailauth.DnsProbeResult;
import com.sealmail.domain.mailauth.DnsProbeStatus;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.SpfPublicationPolicy;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import com.sealmail.domain.policy.DomainName;
import com.sealmail.infra.persistence.entity.MailAuthConfigEntity;
import com.sealmail.infra.persistence.entity.MailAuthDnsProbeEntity;
import com.sealmail.infra.persistence.entity.MailAuthDomainPolicyEntity;
import com.sealmail.infra.persistence.entity.MailAuthPolicyEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class MailAuthPolicyRepositoryImpl implements MailAuthPolicyRepository {

    private static final String DEFAULT_ID = "default";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public MailAuthPolicyRepositoryImpl(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public MailAuthPolicy findPolicy() {
        MailAuthPolicyEntity entity = entityManager.find(MailAuthPolicyEntity.class, DEFAULT_ID);
        if (entity != null) {
            return toDomain(entity);
        }
        return legacyGlobalPolicy();
    }

    @Override
    public MailAuthPolicy savePolicy(MailAuthPolicy policy) {
        MailAuthPolicyEntity entity = toEntity(policy);
        MailAuthPolicyEntity existing = entityManager.find(MailAuthPolicyEntity.class, entity.getId());
        if (existing == null) {
            entityManager.persist(entity);
        } else {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        }
        return policy;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DomainMailAuthPolicy> findDomainPolicy(String domainName) {
        String normalizedDomain = DomainName.requireValid(domainName);
        MailAuthDomainPolicyEntity entity = findDomainEntity(normalizedDomain).orElse(null);
        if (entity != null) {
            return Optional.of(toDomain(entity));
        }
        return legacyDomainPolicy(normalizedDomain);
    }

    @Override
    public DomainMailAuthPolicy saveDomainPolicy(DomainMailAuthPolicy policy) {
        MailAuthDomainPolicyEntity entity = toEntity(policy);
        MailAuthDomainPolicyEntity existing = entityManager.find(MailAuthDomainPolicyEntity.class, entity.getId());
        if (existing == null) {
            findDomainEntity(policy.domainName()).ifPresent(value -> {
                throw new IllegalArgumentException("Mail auth domain policy already exists: " + policy.domainName());
            });
            entityManager.persist(entity);
        } else {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        }
        return policy;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainMailAuthPolicy> findDomainPolicies() {
        TypedQuery<MailAuthDomainPolicyEntity> query = entityManager.createQuery(
                "SELECT p FROM MailAuthDomainPolicyEntity p ORDER BY p.domainName",
                MailAuthDomainPolicyEntity.class);
        return query.getResultList().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public DnsProbeResult saveDnsProbeResult(DnsProbeResult result) {
        MailAuthDnsProbeEntity entity = new MailAuthDnsProbeEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setDomainName(result.domainName());
        entity.setRecordType(result.recordType());
        entity.setExpectedName(result.expectedName());
        entity.setExpectedValueHash(result.expectedValueHash());
        entity.setObservedValue(result.observedValue());
        entity.setStatus(result.status().name());
        entity.setDetail(result.detail());
        entity.setCheckedAt(result.checkedAt());
        entityManager.persist(entity);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DnsProbeResult> findLatestDnsProbeResults(String domainName) {
        if ("*".equals(domainName)) {
            return entityManager.createQuery(
                            "SELECT p FROM MailAuthDnsProbeEntity p ORDER BY p.checkedAt DESC",
                            MailAuthDnsProbeEntity.class)
                    .setMaxResults(30)
                    .getResultList()
                    .stream()
                    .map(this::toDomain)
                    .toList();
        }
        String normalizedDomain = DomainName.requireValid(domainName);
        return entityManager.createQuery(
                        "SELECT p FROM MailAuthDnsProbeEntity p WHERE p.domainName = :domain ORDER BY p.checkedAt DESC",
                        MailAuthDnsProbeEntity.class)
                .setParameter("domain", normalizedDomain)
                .setMaxResults(30)
                .getResultList()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private Optional<MailAuthDomainPolicyEntity> findDomainEntity(String domainName) {
        List<MailAuthDomainPolicyEntity> results = entityManager.createQuery(
                        "SELECT p FROM MailAuthDomainPolicyEntity p WHERE p.domainName = :domain",
                        MailAuthDomainPolicyEntity.class)
                .setParameter("domain", DomainName.requireValid(domainName))
                .getResultList();
        return results.stream().findFirst();
    }

    private MailAuthPolicy legacyGlobalPolicy() {
        MailAuthConfigEntity legacy = entityManager.find(MailAuthConfigEntity.class, DEFAULT_ID);
        if (legacy == null) {
            return MailAuthPolicy.defaults();
        }
        return new MailAuthPolicy(
                DEFAULT_ID,
                legacy.isEnabled(),
                legacy.getAuthservId(),
                legacy.isSkipPrivateRelay() ? TrustedProxyMode.DISABLED : TrustedProxyMode.DISABLED,
                legacy.isDmarcQuarantineRejectPolicy()
                        ? MailAuthFailureAction.APPLY_POLICY
                        : MailAuthFailureAction.LOG_ONLY,
                legacy.getCreatedAt(),
                legacy.getUpdatedAt(),
                legacy.getVersion());
    }

    private Optional<DomainMailAuthPolicy> legacyDomainPolicy(String domainName) {
        MailAuthConfigEntity legacy = entityManager.find(MailAuthConfigEntity.class, DEFAULT_ID);
        if (legacy == null) {
            return Optional.empty();
        }
        return Optional.of(new DomainMailAuthPolicy(
                domainName,
                legacy.isEnabled(),
                new DkimSigningPolicy(
                        legacy.isDkimEnabled(),
                        new DkimSelector(legacy.getDkimSelector()),
                        new DkimKeyRef(legacy.getDkimPrivateKeySecretRef(), legacy.getDkimPrivateKeyPath()),
                        readList(legacy.getDkimSignedHeaders())),
                new SpfPublicationPolicy(
                        legacy.isSpfEnabled(),
                        legacy.isSpfUseA(),
                        legacy.isSpfUseMx(),
                        readList(legacy.getSpfIp4()),
                        readList(legacy.getSpfIp6()),
                        readList(legacy.getSpfIncludes()),
                        legacy.getSpfAllPolicy()),
                new DmarcPublicationPolicy(
                        legacy.isDmarcEnabled(),
                        DmarcPolicyMode.fromTag(legacy.getDmarcPolicy()),
                        DmarcPolicyMode.fromTag(legacy.getDmarcPolicy()),
                        DmarcAlignmentMode.fromTag(legacy.getDmarcAdkim()),
                        DmarcAlignmentMode.fromTag(legacy.getDmarcAspf()),
                        legacy.getDmarcPct(),
                        legacy.getDmarcRua(),
                        legacy.getDmarcRuf()),
                legacy.getCreatedAt(),
                legacy.getUpdatedAt(),
                legacy.getVersion()));
    }

    private MailAuthPolicy toDomain(MailAuthPolicyEntity entity) {
        return new MailAuthPolicy(
                entity.getId(),
                entity.isEnabled(),
                entity.getAuthservId(),
                enumValue(TrustedProxyMode.class, entity.getTrustedProxyMode(), TrustedProxyMode.DISABLED),
                enumValue(MailAuthFailureAction.class, entity.getFailureDefaultAction(), MailAuthFailureAction.LOG_ONLY),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    private MailAuthPolicyEntity toEntity(MailAuthPolicy policy) {
        MailAuthPolicyEntity entity = new MailAuthPolicyEntity();
        entity.setId(policy.id());
        entity.setEnabled(policy.enabled());
        entity.setAuthservId(policy.authservId());
        entity.setTrustedProxyMode(policy.trustedProxyMode().name());
        entity.setFailureDefaultAction(policy.failureDefaultAction().name());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt() != null ? policy.updatedAt() : Instant.now());
        entity.setVersion(policy.version());
        return entity;
    }

    private DomainMailAuthPolicy toDomain(MailAuthDomainPolicyEntity entity) {
        return new DomainMailAuthPolicy(
                entity.getDomainName(),
                entity.isEnabled(),
                new DkimSigningPolicy(
                        entity.isDkimSigningEnabled(),
                        new DkimSelector(entity.getDkimSelector()),
                        new DkimKeyRef(entity.getDkimKeySecretRef(), entity.getDkimKeyPath()),
                        readList(entity.getDkimSignedHeaders())),
                new SpfPublicationPolicy(
                        entity.isSpfPublishEnabled(),
                        entity.isSpfUseA(),
                        entity.isSpfUseMx(),
                        readList(entity.getSpfIp4()),
                        readList(entity.getSpfIp6()),
                        readList(entity.getSpfIncludes()),
                        entity.getSpfAllPolicy()),
                new DmarcPublicationPolicy(
                        entity.isDmarcPublishEnabled(),
                        DmarcPolicyMode.fromTag(entity.getDmarcPolicy()),
                        DmarcPolicyMode.fromTag(entity.getDmarcSubdomainPolicy()),
                        DmarcAlignmentMode.fromTag(entity.getDmarcAdkim()),
                        DmarcAlignmentMode.fromTag(entity.getDmarcAspf()),
                        entity.getDmarcPct(),
                        entity.getDmarcRua(),
                        entity.getDmarcRuf()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    private MailAuthDomainPolicyEntity toEntity(DomainMailAuthPolicy policy) {
        MailAuthDomainPolicyEntity entity = new MailAuthDomainPolicyEntity();
        entity.setId(policy.domainName());
        entity.setDomainName(policy.domainName());
        entity.setEnabled(policy.enabled());
        entity.setDkimSigningEnabled(policy.dkimSigningPolicy().enabled());
        entity.setDkimSelector(policy.dkimSigningPolicy().selector().value());
        entity.setDkimKeySecretRef(policy.dkimSigningPolicy().keyRef().secretRef());
        entity.setDkimKeyPath(policy.dkimSigningPolicy().keyRef().path());
        entity.setDkimSignedHeaders(writeList(policy.dkimSigningPolicy().signedHeaders()));
        entity.setSpfPublishEnabled(policy.spfPublicationPolicy().enabled());
        entity.setSpfUseA(policy.spfPublicationPolicy().useA());
        entity.setSpfUseMx(policy.spfPublicationPolicy().useMx());
        entity.setSpfIp4(writeList(policy.spfPublicationPolicy().ip4()));
        entity.setSpfIp6(writeList(policy.spfPublicationPolicy().ip6()));
        entity.setSpfIncludes(writeList(policy.spfPublicationPolicy().includes()));
        entity.setSpfAllPolicy(policy.spfPublicationPolicy().allPolicy());
        entity.setDmarcPublishEnabled(policy.dmarcPublicationPolicy().enabled());
        entity.setDmarcPolicy(policy.dmarcPublicationPolicy().policy().tagValue());
        entity.setDmarcSubdomainPolicy(policy.dmarcPublicationPolicy().subdomainPolicy().tagValue());
        entity.setDmarcAdkim(policy.dmarcPublicationPolicy().dkimAlignment().tagValue());
        entity.setDmarcAspf(policy.dmarcPublicationPolicy().spfAlignment().tagValue());
        entity.setDmarcPct(policy.dmarcPublicationPolicy().pct());
        entity.setDmarcRua(policy.dmarcPublicationPolicy().rua());
        entity.setDmarcRuf(policy.dmarcPublicationPolicy().ruf());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt() != null ? policy.updatedAt() : Instant.now());
        entity.setVersion(policy.version());
        return entity;
    }

    private DnsProbeResult toDomain(MailAuthDnsProbeEntity entity) {
        return new DnsProbeResult(
                entity.getDomainName(),
                entity.getRecordType(),
                entity.getExpectedName(),
                entity.getExpectedValueHash(),
                entity.getObservedValue(),
                enumValue(DnsProbeStatus.class, entity.getStatus(), DnsProbeStatus.ERROR),
                entity.getDetail(),
                entity.getCheckedAt());
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
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
            throw new IllegalArgumentException("Failed to serialize mail auth policy list", e);
        }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
