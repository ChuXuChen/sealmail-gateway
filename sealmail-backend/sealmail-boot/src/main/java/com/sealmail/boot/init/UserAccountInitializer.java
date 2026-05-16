package com.sealmail.boot.init;

import com.sealmail.app.usecase.bootstrap.SeedUserAccountUseCase;
import com.sealmail.domain.config.SecretReferenceResolver;
import com.sealmail.infra.config.properties.AuthProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountInitializer {

    private final AuthProperties authProperties;
    private final SeedUserAccountUseCase seedUserAccountUseCase;
    private final SecretReferenceResolver secretReferenceResolver;

    @PostConstruct
    public void init() {
        for (AuthProperties.UserRecord record : authProperties.getUsers()) {
            if (record.getUsername() == null || record.getUsername().isBlank()) {
                continue;
            }
            String password = resolvePassword(record);
            if (password == null || password.isBlank()) {
                log.warn("Skipping seed user {} because no password secret reference is configured or resolvable",
                        record.getUsername());
                continue;
            }
            boolean seeded = seedUserAccountUseCase.seed(new SeedUserAccountUseCase.UserSeedCommand(
                    record.getUserId(),
                    record.getUsername(),
                    record.getEmail(),
                    password,
                    record.getRoles(),
                    record.getManagedDomains()
            ));
            if (seeded) {
                log.info("Seeded auth user into PostgreSQL: {}", record.getUsername());
            }
        }
    }

    private String resolvePassword(AuthProperties.UserRecord record) {
        if (record.getPasswordSecretRef() == null || record.getPasswordSecretRef().isBlank()) {
            return null;
        }
        return secretReferenceResolver.resolve(record.getPasswordSecretRef());
    }
}
