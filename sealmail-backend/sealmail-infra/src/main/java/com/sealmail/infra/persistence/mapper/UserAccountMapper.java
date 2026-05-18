package com.sealmail.infra.persistence.mapper;

import com.sealmail.domain.user.UserAccount;
import com.sealmail.infra.persistence.entity.UserAccountEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class UserAccountMapper {

    public UserAccountEntity toEntity(UserAccount userAccount) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(userAccount.getId());
        entity.setUsername(userAccount.getUsername());
        entity.setEmail(userAccount.getEmail());
        entity.setPasswordHash(userAccount.getPasswordHash());
        entity.setRoles(copy(userAccount.getRoles()));
        entity.setManagedDomains(copy(userAccount.getManagedDomains()));
        entity.setActive(userAccount.isActive());
        entity.setLocked(userAccount.isLocked());
        entity.setFailedLoginAttempts(userAccount.getFailedLoginAttempts());
        entity.setLockoutExpiresAt(userAccount.getLockoutExpiresAt());
        entity.setLastPasswordChangedAt(userAccount.getLastPasswordChangedAt());
        entity.setLastLoginAt(userAccount.getLastLoginAt());
        entity.setLastLoginIp(userAccount.getLastLoginIp());
        entity.setTokenInvalidBefore(userAccount.getTokenInvalidBefore());
        return entity;
    }

    public UserAccount toDomain(UserAccountEntity entity) {
        return UserAccount.restore(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPasswordHash(),
                copy(entity.getRoles()),
                copy(entity.getManagedDomains()),
                entity.isActive(),
                entity.isLocked(),
                entity.getFailedLoginAttempts(),
                entity.getLockoutExpiresAt(),
                entity.getLastPasswordChangedAt(),
                entity.getLastLoginAt(),
                entity.getLastLoginIp(),
                entity.getTokenInvalidBefore()
        );
    }

    private Set<String> copy(Set<String> values) {
        return values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(values);
    }
}
