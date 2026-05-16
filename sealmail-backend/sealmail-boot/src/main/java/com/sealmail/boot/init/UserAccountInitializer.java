package com.sealmail.boot.init;

import com.sealmail.app.usecase.bootstrap.SeedUserAccountUseCase;
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

    @PostConstruct
    public void init() {
        for (AuthProperties.UserRecord record : authProperties.getUsers()) {
            if (record.getUsername() == null || record.getUsername().isBlank()) {
                continue;
            }
            if (record.getPassword() == null || record.getPassword().isBlank()) {
                log.warn("Skipping seed user {} because no password is configured", record.getUsername());
                continue;
            }
            boolean seeded = seedUserAccountUseCase.seed(new SeedUserAccountUseCase.UserSeedCommand(
                    record.getUserId(),
                    record.getUsername(),
                    record.getEmail(),
                    record.getPassword(),
                    record.getRoles(),
                    record.getManagedDomains()
            ));
            if (seeded) {
                log.info("Seeded auth user into PostgreSQL: {}", record.getUsername());
            }
        }
    }
}
