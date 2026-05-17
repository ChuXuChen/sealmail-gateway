package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import com.sealmail.infra.persistence.entity.UserAccountEntity;
import com.sealmail.infra.persistence.mapper.UserAccountMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@Transactional
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final EntityManager entityManager;
    private final UserAccountMapper mapper;

    public UserAccountRepositoryImpl(EntityManager entityManager, UserAccountMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        UserAccountEntity entity = mapper.toEntity(userAccount);
        entity.setUpdatedAt(Instant.now());
        UserAccountEntity existing = entityManager.find(UserAccountEntity.class, entity.getId());
        if (existing != null) {
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(existing.getVersion());
            entityManager.merge(entity);
        } else {
            entity.setCreatedAt(Instant.now());
            entityManager.persist(entity);
        }
        return userAccount;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(String id) {
        UserAccountEntity entity = entityManager.find(UserAccountEntity.class, id);
        return Optional.ofNullable(entity).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        TypedQuery<UserAccountEntity> query = entityManager.createQuery(
                "SELECT u FROM UserAccountEntity u WHERE LOWER(u.username) = :username",
                UserAccountEntity.class
        );
        query.setParameter("username", username == null ? null : username.toLowerCase(Locale.ROOT));
        List<UserAccountEntity> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(mapper.toDomain(results.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(u) FROM UserAccountEntity u WHERE LOWER(u.username) = :username",
                        Long.class
                )
                .setParameter("username", username == null ? null : username.toLowerCase(Locale.ROOT))
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findAll() {
        return entityManager.createQuery(
                        "SELECT u FROM UserAccountEntity u ORDER BY u.username",
                        UserAccountEntity.class
                ).getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
