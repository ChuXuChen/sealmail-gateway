package com.sealmail.infra.persistence.mapper;

import com.sealmail.domain.user.UserAccount;
import com.sealmail.infra.persistence.entity.UserAccountEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserAccountMapper {

    public UserAccountEntity toEntity(UserAccount userAccount) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(userAccount.getId());
        entity.setUsername(userAccount.getUsername());
        entity.setEmail(userAccount.getEmail());
        entity.setPasswordHash(userAccount.getPasswordHash());
        entity.setRoles(join(userAccount.getRoles()));
        entity.setManagedDomains(join(userAccount.getManagedDomains()));
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
                split(entity.getRoles()),
                split(entity.getManagedDomains()),
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

    private String join(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().collect(Collectors.joining(","));
    }

    private Set<String> split(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
