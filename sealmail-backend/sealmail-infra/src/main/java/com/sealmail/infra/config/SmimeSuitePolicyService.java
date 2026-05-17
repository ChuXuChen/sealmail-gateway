package com.sealmail.infra.config;

import com.sealmail.domain.config.SmimeSuitePolicyPort;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.policy.event.SmimeSuitePolicyChanged;
import com.sealmail.infra.config.properties.SmimeCryptoProperties;
import com.sealmail.infra.crypto.SmimeAlgorithmSuite;
import com.sealmail.infra.crypto.SmimeAlgorithmSuites;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.SmimeSuitePolicyEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class SmimeSuitePolicyService implements SmimeSuitePolicyPort {

    private static final String DEFAULT_ID = "default";

    private final EntityManager entityManager;
    private final DomainEventPublisher domainEventPublisher;
    private final SmimeCryptoProperties smimeCryptoProperties;
    private final TransactionTemplate initializationTransaction;

    public SmimeSuitePolicyService(EntityManager entityManager,
                                   DomainEventPublisher domainEventPublisher,
                                   SmimeCryptoProperties smimeCryptoProperties,
                                   PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.domainEventPublisher = domainEventPublisher;
        this.smimeCryptoProperties = smimeCryptoProperties;
        this.initializationTransaction = new TransactionTemplate(transactionManager);
        this.initializationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public SmimeSuitePolicySettings getSettings() {
        return toSettings(entity(), suites());
    }

    @Override
    public SmimeSuitePolicySettings updateSettings(SmimeSuitePolicySettingsUpdate update) {
        SmimeAlgorithmSuites suites = suites();
        SmimeSuitePolicyEntity entity = entity();
        applyUpdate(entity, update, suites);
        validateEntity(entity, suites);
        entity.setUpdatedAt(Instant.now());
        SmimeSuitePolicyEntity saved = entityManager.merge(entity);
        domainEventPublisher.publishEvent(new SmimeSuitePolicyChanged(DEFAULT_ID, changedFields(update)));
        return toSettings(saved, suites);
    }

    public SmimeAlgorithmSuites effectiveSuites() {
        SmimeSuitePolicyEntity entity = entity();
        SmimeCryptoProperties properties = copyPropertiesWithDefaults(
                entity.getDefaultStandardSuite(),
                entity.getDefaultGmSuite());
        return new SmimeAlgorithmSuites(properties);
    }

    private SmimeSuitePolicyEntity entity() {
        SmimeSuitePolicyEntity entity = entityManager.find(SmimeSuitePolicyEntity.class, DEFAULT_ID);
        if (entity != null) {
            return entity;
        }
        return initializationTransaction.execute(status -> {
            SmimeSuitePolicyEntity existing = entityManager.find(SmimeSuitePolicyEntity.class, DEFAULT_ID);
            if (existing != null) {
                return existing;
            }
            SmimeSuitePolicyEntity created = defaultEntity();
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }

    private SmimeSuitePolicyEntity defaultEntity() {
        Instant now = Instant.now();
        SmimeSuitePolicyEntity entity = new SmimeSuitePolicyEntity();
        entity.setId(DEFAULT_ID);
        entity.setDefaultStandardSuite(defaultSuiteId(smimeCryptoProperties.getDefaultStandardSuite(),
                SmimeAlgorithmSuites.STANDARD_AES_256_CBC));
        entity.setDefaultGmSuite(defaultSuiteId(smimeCryptoProperties.getDefaultGmSuite(),
                SmimeAlgorithmSuites.GM_SM4_CBC));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyUpdate(SmimeSuitePolicyEntity entity,
                             SmimeSuitePolicySettingsUpdate update,
                             SmimeAlgorithmSuites suites) {
        if (update == null) {
            return;
        }
        if (update.defaultStandardSuite() != null) {
            String suiteId = requireText(update.defaultStandardSuite(), "Standard S/MIME suite cannot be blank");
            requireProfile(suites, suiteId, CryptoProfile.STANDARD);
            entity.setDefaultStandardSuite(suiteId);
        }
        if (update.defaultGmSuite() != null) {
            String suiteId = requireText(update.defaultGmSuite(), "GM S/MIME suite cannot be blank");
            requireProfile(suites, suiteId, CryptoProfile.GM);
            entity.setDefaultGmSuite(suiteId);
        }
    }

    private void validateEntity(SmimeSuitePolicyEntity entity, SmimeAlgorithmSuites suites) {
        requireProfile(suites, entity.getDefaultStandardSuite(), CryptoProfile.STANDARD);
        requireProfile(suites, entity.getDefaultGmSuite(), CryptoProfile.GM);
    }

    private SmimeSuitePolicySettings toSettings(SmimeSuitePolicyEntity entity, SmimeAlgorithmSuites suites) {
        validateEntity(entity, suites);
        return new SmimeSuitePolicySettings(
                entity.getDefaultStandardSuite(),
                entity.getDefaultGmSuite(),
                options(suites, CryptoProfile.STANDARD),
                options(suites, CryptoProfile.GM),
                entity.getUpdatedAt());
    }

    private List<SmimeSuiteOption> options(SmimeAlgorithmSuites suites, CryptoProfile profile) {
        return suites.forProfile(profile).stream()
                .map(suite -> new SmimeSuiteOption(
                        suite.id(),
                        suite.displayName(),
                        suite.profile().name()))
                .toList();
    }

    private SmimeAlgorithmSuites suites() {
        return new SmimeAlgorithmSuites(smimeCryptoProperties);
    }

    private SmimeCryptoProperties copyPropertiesWithDefaults(String defaultStandardSuite, String defaultGmSuite) {
        SmimeCryptoProperties properties = new SmimeCryptoProperties();
        properties.setDefaultStandardSuite(defaultSuiteId(defaultStandardSuite, SmimeAlgorithmSuites.STANDARD_AES_256_CBC));
        properties.setDefaultGmSuite(defaultSuiteId(defaultGmSuite, SmimeAlgorithmSuites.GM_SM4_CBC));
        properties.setSuites(smimeCryptoProperties.getSuites());
        return properties;
    }

    private void requireProfile(SmimeAlgorithmSuites suites, String suiteId, CryptoProfile profile) {
        SmimeAlgorithmSuite suite = suites.get(suiteId);
        if (suite.profile() != profile) {
            throw new IllegalArgumentException("S/MIME suite " + suiteId + " does not belong to " + profile);
        }
    }

    private List<String> changedFields(SmimeSuitePolicySettingsUpdate update) {
        if (update == null) {
            return List.of("GENERAL");
        }
        Set<String> fields = new LinkedHashSet<>();
        if (update.defaultStandardSuite() != null) {
            fields.add("defaultStandardSuite");
        }
        if (update.defaultGmSuite() != null) {
            fields.add("defaultGmSuite");
        }
        return fields.isEmpty() ? List.of("GENERAL") : List.copyOf(fields);
    }

    private String defaultSuiteId(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String requireText(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
