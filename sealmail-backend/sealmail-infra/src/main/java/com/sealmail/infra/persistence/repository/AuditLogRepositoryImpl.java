package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import com.sealmail.infra.persistence.entity.AuditLogEntity;
import com.sealmail.infra.persistence.mapper.AuditLogMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class AuditLogRepositoryImpl implements AuditLogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final AuditLogMapper mapper;

    public AuditLogRepositoryImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog save(AuditLog auditLog) {
        AuditLogEntity entity = mapper.toEntity(auditLog);
        entityManager.persist(entity);
        return auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditLog> findById(String id) {
        AuditLogEntity entity = entityManager.find(AuditLogEntity.class, id);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findByType(AuditLogType type, int page, int size) {
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.type = :type ORDER BY a.occurredAt DESC",
                AuditLogEntity.class
        );
        query.setParameter("type", type.name());
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findByUserId(String userId, int page, int size) {
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.userId = :userId ORDER BY a.occurredAt DESC",
                AuditLogEntity.class
        );
        query.setParameter("userId", userId);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findByResource(String resourceType, String resourceId, int page, int size) {
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.resourceType = :resourceType"
                        + " AND a.resourceId = :resourceId ORDER BY a.occurredAt DESC",
                AuditLogEntity.class
        );
        query.setParameter("resourceType", resourceType);
        query.setParameter("resourceId", resourceId);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findByTimeRange(Instant startTime, Instant endTime, int page, int size) {
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.occurredAt BETWEEN :startTime AND :endTime ORDER BY a.occurredAt DESC",
                AuditLogEntity.class
        );
        query.setParameter("startTime", startTime);
        query.setParameter("endTime", endTime);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> findAll(int page, int size) {
        TypedQuery<AuditLogEntity> query = entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a ORDER BY a.occurredAt DESC",
                AuditLogEntity.class
        );
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> search(List<AuditLogType> types, Boolean success, int page, int size) {
        StringBuilder jpql = new StringBuilder("SELECT a FROM AuditLogEntity a WHERE 1 = 1");
        if (types != null && !types.isEmpty()) {
            jpql.append(" AND a.type IN :types");
        }
        if (success != null) {
            jpql.append(" AND a.success = :success");
        }
        jpql.append(" ORDER BY a.occurredAt DESC");

        TypedQuery<AuditLogEntity> query = entityManager.createQuery(jpql.toString(), AuditLogEntity.class);
        applySearchParameters(query, types, success);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLogEntity a",
                Long.class
        ).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByType(AuditLogType type) {
        return entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.type = :type",
                Long.class
        ).setParameter("type", type.name()).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserId(String userId) {
        return entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.userId = :userId",
                Long.class
        ).setParameter("userId", userId).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByResource(String resourceType, String resourceId) {
        return entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.resourceType = :resourceType"
                        + " AND a.resourceId = :resourceId",
                Long.class
        ).setParameter("resourceType", resourceType)
                .setParameter("resourceId", resourceId)
                .getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByTimeRange(Instant startTime, Instant endTime) {
        return entityManager.createQuery(
                "SELECT COUNT(a) FROM AuditLogEntity a WHERE a.occurredAt BETWEEN :startTime AND :endTime",
                Long.class
        ).setParameter("startTime", startTime)
                .setParameter("endTime", endTime)
                .getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public long countSearch(List<AuditLogType> types, Boolean success) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(a) FROM AuditLogEntity a WHERE 1 = 1");
        if (types != null && !types.isEmpty()) {
            jpql.append(" AND a.type IN :types");
        }
        if (success != null) {
            jpql.append(" AND a.success = :success");
        }
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        applySearchParameters(query, types, success);
        return query.getSingleResult();
    }

    private void applySearchParameters(jakarta.persistence.Query query, List<AuditLogType> types, Boolean success) {
        if (types != null && !types.isEmpty()) {
            query.setParameter("types", types.stream().map(AuditLogType::name).toList());
        }
        if (success != null) {
            query.setParameter("success", success);
        }
    }
}
