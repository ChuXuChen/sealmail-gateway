package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.key.KeyRecord;
import com.sealmail.domain.key.KeyRecordRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.ManagedKeyEntity;
import com.sealmail.infra.persistence.mapper.ManagedKeyMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class ManagedKeyRepositoryImpl implements KeyRecordRepository {

    private final EntityManager entityManager;
    private final ManagedKeyMapper mapper;

    public ManagedKeyRepositoryImpl(EntityManager entityManager, ManagedKeyMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public KeyRecord save(KeyRecord keyRecord) {
        ManagedKeyEntity entity = mapper.toEntity(keyRecord);
        entity.setUpdatedAt(Instant.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        ManagedKeyEntity existing = entityManager.find(ManagedKeyEntity.class, entity.getKeyId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        return keyRecord;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KeyRecord> findById(String keyId) {
        ManagedKeyEntity entity = entityManager.find(ManagedKeyEntity.class, keyId);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KeyRecord> findActiveByCertificateId(String certificateId) {
        if (certificateId == null || certificateId.isBlank()) {
            return Optional.empty();
        }
        TypedQuery<ManagedKeyEntity> query = entityManager.createQuery(
                "SELECT k FROM ManagedKeyEntity k WHERE k.certificateId = :certificateId " +
                        "AND k.status = 'ACTIVE' ORDER BY k.createdAt DESC",
                ManagedKeyEntity.class);
        query.setParameter("certificateId", certificateId);
        return query.getResultList().stream().findFirst().map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeyRecord> findActiveByOwnerAndPurpose(EmailAddress owner, KeyPurpose purpose) {
        TypedQuery<ManagedKeyEntity> query = entityManager.createQuery(
                "SELECT k FROM ManagedKeyEntity k WHERE k.ownerEmail = :ownerEmail " +
                        "AND k.purpose = :purpose AND k.status = 'ACTIVE' ORDER BY k.createdAt DESC",
                ManagedKeyEntity.class);
        query.setParameter("ownerEmail", owner.getValue());
        query.setParameter("purpose", purpose.name());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }
}
