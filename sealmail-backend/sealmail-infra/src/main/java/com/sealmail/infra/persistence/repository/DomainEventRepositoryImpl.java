package com.sealmail.infra.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.certificate.event.AliasAssigned;
import com.sealmail.domain.certificate.event.CertificateBindingChanged;
import com.sealmail.domain.certificate.event.CertificateDeleted;
import com.sealmail.domain.certificate.event.CertificateImported;
import com.sealmail.domain.certificate.event.CertificateIssued;
import com.sealmail.domain.certificate.event.CertificateRevoked;
import com.sealmail.domain.certificate.event.CertificateTrusted;
import com.sealmail.domain.certificate.event.CertificateUntrusted;
import com.sealmail.domain.certificate.event.KeyUsagesUpdated;
import com.sealmail.domain.exceptionmail.event.ExceptionMailCreated;
import com.sealmail.domain.mailsecurity.event.MailDecrypted;
import com.sealmail.domain.mailsecurity.event.MailDelivered;
import com.sealmail.domain.mailsecurity.event.MailEncrypted;
import com.sealmail.domain.mailsecurity.event.MailQuarantined;
import com.sealmail.domain.mailsecurity.event.MailReceived;
import com.sealmail.domain.mailsecurity.event.MailSigned;
import com.sealmail.domain.mailsecurity.event.MailVerified;
import com.sealmail.domain.policy.event.DkimSettingChanged;
import com.sealmail.domain.policy.event.DlpPatternConfigChanged;
import com.sealmail.domain.policy.event.DlpSelectionConfigChanged;
import com.sealmail.domain.policy.event.DomainConfigActivationChanged;
import com.sealmail.domain.policy.event.DomainConfigCreated;
import com.sealmail.domain.policy.event.DomainConfigDeleted;
import com.sealmail.domain.policy.event.EncryptionPolicyChanged;
import com.sealmail.domain.policy.event.GmEdgePolicyChanged;
import com.sealmail.domain.policy.event.MailAuthConfigChanged;
import com.sealmail.domain.policy.event.PreferredAlgorithmChanged;
import com.sealmail.domain.policy.event.QuarantinePolicyChanged;
import com.sealmail.domain.policy.event.RelayPolicyChanged;
import com.sealmail.domain.policy.event.SigningDisabled;
import com.sealmail.domain.policy.event.SigningEnabled;
import com.sealmail.domain.policy.event.SmimeSuitePolicyChanged;
import com.sealmail.domain.quarantine.event.QuarantineCreated;
import com.sealmail.domain.quarantine.event.QuarantineRejected;
import com.sealmail.domain.quarantine.event.QuarantineReleaseRestored;
import com.sealmail.domain.quarantine.event.QuarantineReleased;
import com.sealmail.domain.shared.event.AuditEvent;
import com.sealmail.domain.shared.event.DomainEvent;
import com.sealmail.domain.shared.repository.DomainEventRepository;
import com.sealmail.infra.persistence.entity.DomainEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Repository
@Transactional
public class DomainEventRepositoryImpl implements DomainEventRepository {

    private static final Map<String, Class<? extends DomainEvent>> EVENT_TYPES = Map.ofEntries(
            Map.entry(AliasAssigned.class.getSimpleName(), AliasAssigned.class),
            Map.entry(CertificateBindingChanged.class.getSimpleName(), CertificateBindingChanged.class),
            Map.entry(CertificateDeleted.class.getSimpleName(), CertificateDeleted.class),
            Map.entry(CertificateImported.class.getSimpleName(), CertificateImported.class),
            Map.entry(CertificateIssued.class.getSimpleName(), CertificateIssued.class),
            Map.entry(CertificateRevoked.class.getSimpleName(), CertificateRevoked.class),
            Map.entry(CertificateTrusted.class.getSimpleName(), CertificateTrusted.class),
            Map.entry(CertificateUntrusted.class.getSimpleName(), CertificateUntrusted.class),
            Map.entry(KeyUsagesUpdated.class.getSimpleName(), KeyUsagesUpdated.class),
            Map.entry(ExceptionMailCreated.class.getSimpleName(), ExceptionMailCreated.class),
            Map.entry(MailDecrypted.class.getSimpleName(), MailDecrypted.class),
            Map.entry(MailDelivered.class.getSimpleName(), MailDelivered.class),
            Map.entry(MailEncrypted.class.getSimpleName(), MailEncrypted.class),
            Map.entry(MailQuarantined.class.getSimpleName(), MailQuarantined.class),
            Map.entry(MailReceived.class.getSimpleName(), MailReceived.class),
            Map.entry(MailSigned.class.getSimpleName(), MailSigned.class),
            Map.entry(MailVerified.class.getSimpleName(), MailVerified.class),
            Map.entry(DkimSettingChanged.class.getSimpleName(), DkimSettingChanged.class),
            Map.entry(DlpPatternConfigChanged.class.getSimpleName(), DlpPatternConfigChanged.class),
            Map.entry(DlpSelectionConfigChanged.class.getSimpleName(), DlpSelectionConfigChanged.class),
            Map.entry(DomainConfigActivationChanged.class.getSimpleName(), DomainConfigActivationChanged.class),
            Map.entry(DomainConfigCreated.class.getSimpleName(), DomainConfigCreated.class),
            Map.entry(DomainConfigDeleted.class.getSimpleName(), DomainConfigDeleted.class),
            Map.entry(EncryptionPolicyChanged.class.getSimpleName(), EncryptionPolicyChanged.class),
            Map.entry(GmEdgePolicyChanged.class.getSimpleName(), GmEdgePolicyChanged.class),
            Map.entry(MailAuthConfigChanged.class.getSimpleName(), MailAuthConfigChanged.class),
            Map.entry(PreferredAlgorithmChanged.class.getSimpleName(), PreferredAlgorithmChanged.class),
            Map.entry(QuarantinePolicyChanged.class.getSimpleName(), QuarantinePolicyChanged.class),
            Map.entry(RelayPolicyChanged.class.getSimpleName(), RelayPolicyChanged.class),
            Map.entry(SigningDisabled.class.getSimpleName(), SigningDisabled.class),
            Map.entry(SigningEnabled.class.getSimpleName(), SigningEnabled.class),
            Map.entry(SmimeSuitePolicyChanged.class.getSimpleName(), SmimeSuitePolicyChanged.class),
            Map.entry(QuarantineCreated.class.getSimpleName(), QuarantineCreated.class),
            Map.entry(QuarantineRejected.class.getSimpleName(), QuarantineRejected.class),
            Map.entry(QuarantineReleaseRestored.class.getSimpleName(), QuarantineReleaseRestored.class),
            Map.entry(QuarantineReleased.class.getSimpleName(), QuarantineReleased.class),
            Map.entry(AuditEvent.class.getSimpleName(), AuditEvent.class)
    );

    private static final Map<Class<? extends DomainEvent>, Function<DomainEvent, String>> AGGREGATE_IDS = Map.ofEntries(
            Map.entry(AliasAssigned.class, event -> ((AliasAssigned) event).getCertificateId().toString()),
            Map.entry(CertificateBindingChanged.class, event -> ((CertificateBindingChanged) event).getBindingId()),
            Map.entry(CertificateDeleted.class, event -> ((CertificateDeleted) event).getCertificateId().toString()),
            Map.entry(CertificateImported.class, event -> ((CertificateImported) event).getCertificateId().toString()),
            Map.entry(CertificateIssued.class, event -> ((CertificateIssued) event).getCertificateId().toString()),
            Map.entry(CertificateRevoked.class, event -> ((CertificateRevoked) event).getCertificateId().toString()),
            Map.entry(CertificateTrusted.class, event -> ((CertificateTrusted) event).getCertificateId().toString()),
            Map.entry(CertificateUntrusted.class, event -> ((CertificateUntrusted) event).getCertificateId().toString()),
            Map.entry(KeyUsagesUpdated.class, event -> ((KeyUsagesUpdated) event).getCertificateId().toString()),
            Map.entry(ExceptionMailCreated.class, event -> ((ExceptionMailCreated) event).getExceptionMailId()),
            Map.entry(MailDecrypted.class, event -> ((MailDecrypted) event).getMessageId()),
            Map.entry(MailDelivered.class, event -> ((MailDelivered) event).getMessageId()),
            Map.entry(MailEncrypted.class, event -> ((MailEncrypted) event).getMessageId()),
            Map.entry(MailQuarantined.class, event -> ((MailQuarantined) event).getMessageId()),
            Map.entry(MailReceived.class, event -> ((MailReceived) event).getMessageId()),
            Map.entry(MailSigned.class, event -> ((MailSigned) event).getMessageId()),
            Map.entry(MailVerified.class, event -> ((MailVerified) event).getMessageId()),
            Map.entry(DkimSettingChanged.class, event -> ((DkimSettingChanged) event).getConfigId()),
            Map.entry(DlpPatternConfigChanged.class, event -> ((DlpPatternConfigChanged) event).getPatternId()),
            Map.entry(DlpSelectionConfigChanged.class, event -> ((DlpSelectionConfigChanged) event).getSelectionId()),
            Map.entry(DomainConfigActivationChanged.class, event -> ((DomainConfigActivationChanged) event).getConfigId()),
            Map.entry(DomainConfigCreated.class, event -> ((DomainConfigCreated) event).getConfigId()),
            Map.entry(DomainConfigDeleted.class, event -> ((DomainConfigDeleted) event).getConfigId()),
            Map.entry(EncryptionPolicyChanged.class, event -> ((EncryptionPolicyChanged) event).getConfigId()),
            Map.entry(GmEdgePolicyChanged.class, event -> ((GmEdgePolicyChanged) event).getConfigId()),
            Map.entry(MailAuthConfigChanged.class, event -> ((MailAuthConfigChanged) event).getConfigId()),
            Map.entry(PreferredAlgorithmChanged.class, event -> ((PreferredAlgorithmChanged) event).getConfigId()),
            Map.entry(QuarantinePolicyChanged.class, event -> ((QuarantinePolicyChanged) event).getConfigId()),
            Map.entry(RelayPolicyChanged.class, event -> ((RelayPolicyChanged) event).getConfigId()),
            Map.entry(SigningDisabled.class, event -> ((SigningDisabled) event).getConfigId()),
            Map.entry(SigningEnabled.class, event -> ((SigningEnabled) event).getConfigId()),
            Map.entry(SmimeSuitePolicyChanged.class, event -> ((SmimeSuitePolicyChanged) event).getConfigId()),
            Map.entry(QuarantineCreated.class, event -> ((QuarantineCreated) event).getQuarantineId()),
            Map.entry(QuarantineRejected.class, event -> ((QuarantineRejected) event).getQuarantineId()),
            Map.entry(QuarantineReleaseRestored.class, event -> ((QuarantineReleaseRestored) event).getQuarantineId()),
            Map.entry(QuarantineReleased.class, event -> ((QuarantineReleased) event).getQuarantineId()),
            Map.entry(AuditEvent.class, event -> ((AuditEvent) event).getResourceId())
    );

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public DomainEventRepositoryImpl(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(DomainEvent event) {
        DomainEventEntity entity = new DomainEventEntity();
        entity.setEventId(event.getEventId());
        entity.setAggregateId(extractAggregateId(event));
        entity.setAggregateType(event.getClass().getEnclosingClass() != null
                ? event.getClass().getEnclosingClass().getSimpleName()
                : "DomainEvent");
        entity.setEventType(event.getClass().getSimpleName());
        entity.setOccurredAt(event.getOccurredAt());
        try {
            entity.setEventData(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize domain event", e);
        }
        entityManager.persist(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findByAggregateId(String aggregateId) {
        TypedQuery<DomainEventEntity> query = entityManager.createQuery(
                "SELECT e FROM DomainEventEntity e WHERE e.aggregateId = :id ORDER BY e.occurredAt",
                DomainEventEntity.class
        );
        query.setParameter("id", aggregateId);
        return query.getResultList().stream()
                .map(this::deserializeEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findAll() {
        TypedQuery<DomainEventEntity> query = entityManager.createQuery(
                "SELECT e FROM DomainEventEntity e ORDER BY e.occurredAt",
                DomainEventEntity.class
        );
        return query.getResultList().stream()
                .map(this::deserializeEvent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private String extractAggregateId(DomainEvent event) {
        Function<DomainEvent, String> extractor = AGGREGATE_IDS.get(event.getClass());
        if (extractor == null) {
            return "unknown";
        }
        String value = extractor.apply(event);
        return value != null && !value.isBlank() ? value : "unknown";
    }

    private Optional<DomainEvent> deserializeEvent(DomainEventEntity entity) {
        try {
            Class<? extends DomainEvent> clazz = EVENT_TYPES.get(entity.getEventType());
            if (clazz == null) {
                return Optional.empty();
            }
            return Optional.of((DomainEvent) objectMapper.readValue(entity.getEventData(), clazz));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
