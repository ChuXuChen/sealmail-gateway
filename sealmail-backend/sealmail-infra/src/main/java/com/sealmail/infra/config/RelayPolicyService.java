package com.sealmail.infra.config;

import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.domain.mailsecurity.RelayProfile;
import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;
import com.sealmail.domain.policy.event.RelayPolicyChanged;
import com.sealmail.infra.events.DomainEventPublisher;
import com.sealmail.infra.persistence.entity.RelayPolicyEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional
public class RelayPolicyService implements RelayPolicyPort {

    private static final String DEFAULT_ID = "default";

    @PersistenceContext
    private EntityManager entityManager;

    private final DomainEventPublisher domainEventPublisher;
    private final SecretReferenceResolver secretReferenceResolver;
    private final TransactionTemplate initializationTransaction;

    public RelayPolicyService(DomainEventPublisher domainEventPublisher,
                              SecretReferenceResolver secretReferenceResolver,
                              PlatformTransactionManager transactionManager) {
        this.domainEventPublisher = domainEventPublisher;
        this.secretReferenceResolver = secretReferenceResolver;
        this.initializationTransaction = new TransactionTemplate(transactionManager);
        this.initializationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public RelayPolicySettings getSettings() {
        return toSettings(entity());
    }

    @Override
    @Transactional(readOnly = true)
    public RelayProbeSettings getProbeSettings() {
        RelayPolicyEntity entity = entity();
        String password = hasText(entity.getPasswordSecretRef())
                ? secretReferenceResolver.resolve(entity.getPasswordSecretRef())
                : "";
        return new RelayProbeSettings(
                entity.isEnabled(),
                entity.getHost(),
                entity.getPort(),
                effectiveTransportSecurity(entity).usesTls(),
                effectiveTransportSecurity(entity),
                emptyToBlank(entity.getUsername()),
                emptyToBlank(password),
                entity.getTimeoutMs());
    }

    @Override
    public RelayPolicySettings updateSettings(RelayPolicySettingsUpdate update) {
        RelayPolicyEntity entity = entity();
        applyUpdate(entity, update);
        entity.setUpdatedAt(Instant.now());
        RelayPolicyEntity saved = entityManager.merge(entity);
        domainEventPublisher.publishEvent(new RelayPolicyChanged(DEFAULT_ID, changedFields(update)));
        return toSettings(saved);
    }

    @Transactional(readOnly = true)
    public RelayProfile activeRelayProfile() {
        RelayPolicyEntity entity = entity();
        if (!entity.isEnabled() || !hasText(entity.getHost())) {
            return null;
        }
        String password = hasText(entity.getPasswordSecretRef())
                ? secretReferenceResolver.resolve(entity.getPasswordSecretRef())
                : "";
        return new RelayProfile(
                entity.getHost(),
                entity.getPort(),
                effectiveTransportSecurity(entity),
                emptyToBlank(entity.getUsername()),
                emptyToBlank(password),
                entity.getTimeoutMs(),
                entity.getEnvelopeFrom());
    }

    private RelayPolicyEntity entity() {
        RelayPolicyEntity entity = entityManager.find(RelayPolicyEntity.class, DEFAULT_ID);
        if (entity != null) {
            return entity;
        }
        return initializationTransaction.execute(status -> {
            RelayPolicyEntity existing = entityManager.find(RelayPolicyEntity.class, DEFAULT_ID);
            if (existing != null) {
                return existing;
            }
            RelayPolicyEntity created = defaultEntity();
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }

    private RelayPolicyEntity defaultEntity() {
        Instant now = Instant.now();
        RelayPolicyEntity entity = new RelayPolicyEntity();
        entity.setId(DEFAULT_ID);
        entity.setEnabled(false);
        entity.setHost("localhost");
        entity.setPort(25);
        entity.setUseTls(false);
        entity.setTransportSecurity(SmtpTransportSecurity.NONE);
        entity.setUsername(null);
        entity.setPasswordSecretRef(null);
        entity.setTimeoutMs(30000);
        entity.setEnvelopeFrom(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private void applyUpdate(RelayPolicyEntity entity, RelayPolicySettingsUpdate update) {
        if (update == null) {
            return;
        }
        if (update.enabled() != null) {
            entity.setEnabled(update.enabled());
        }
        if (update.host() != null) {
            entity.setHost(requireText(update.host(), "Relay host cannot be blank"));
        }
        if (update.port() != null) {
            require(update.port() > 0 && update.port() <= 65535, "Relay port must be between 1 and 65535");
            entity.setPort(update.port());
        }
        if (update.transportSecurity() != null) {
            setTransportSecurity(entity, update.transportSecurity());
        } else if (update.useTls() != null) {
            int port = update.port() != null ? update.port() : entity.getPort();
            setTransportSecurity(entity, SmtpTransportSecurity.fromLegacyUseTls(update.useTls(), port));
        }
        if (update.username() != null) {
            entity.setUsername(blankToNull(update.username()));
        }
        if (Boolean.TRUE.equals(update.clearPasswordSecretRef())) {
            entity.setPasswordSecretRef(null);
        } else if (update.passwordSecretRef() != null) {
            entity.setPasswordSecretRef(blankToNull(update.passwordSecretRef()));
        }
        if (update.timeoutMs() != null) {
            require(update.timeoutMs() > 0, "Relay timeout must be positive");
            entity.setTimeoutMs(update.timeoutMs());
        }
        if (update.envelopeFrom() != null) {
            entity.setEnvelopeFrom(blankToNull(update.envelopeFrom()));
        }
    }

    private RelayPolicySettings toSettings(RelayPolicyEntity entity) {
        return new RelayPolicySettings(
                entity.isEnabled(),
                entity.getHost(),
                entity.getPort(),
                effectiveTransportSecurity(entity).usesTls(),
                effectiveTransportSecurity(entity),
                entity.getUsername(),
                hasText(entity.getPasswordSecretRef()),
                entity.getPasswordSecretRef(),
                entity.getTimeoutMs(),
                entity.getEnvelopeFrom(),
                entity.getUpdatedAt());
    }

    private List<String> changedFields(RelayPolicySettingsUpdate update) {
        if (update == null) {
            return List.of("GENERAL");
        }
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (update.enabled() != null) {
            fields.add("enabled");
        }
        if (update.host() != null || update.port() != null || update.useTls() != null
                || update.transportSecurity() != null || update.timeoutMs() != null) {
            fields.add("connection");
        }
        if (update.username() != null || update.passwordSecretRef() != null || update.clearPasswordSecretRef() != null) {
            fields.add("authentication");
        }
        if (update.envelopeFrom() != null) {
            fields.add("envelopeFrom");
        }
        return fields.isEmpty() ? List.of("GENERAL") : List.copyOf(fields);
    }

    private String requireText(String value, String message) {
        String trimmed = value != null ? value.trim() : "";
        require(!trimmed.isBlank(), message);
        return trimmed;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String emptyToBlank(String value) {
        return value != null ? value : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private SmtpTransportSecurity effectiveTransportSecurity(RelayPolicyEntity entity) {
        SmtpTransportSecurity transportSecurity = entity.getTransportSecurity();
        if (transportSecurity != null) {
            return transportSecurity;
        }
        return SmtpTransportSecurity.fromLegacyUseTls(entity.isUseTls(), entity.getPort());
    }

    private void setTransportSecurity(RelayPolicyEntity entity, SmtpTransportSecurity transportSecurity) {
        SmtpTransportSecurity normalized = transportSecurity == null ? SmtpTransportSecurity.NONE : transportSecurity;
        entity.setTransportSecurity(normalized);
        entity.setUseTls(normalized.usesTls());
    }
}
