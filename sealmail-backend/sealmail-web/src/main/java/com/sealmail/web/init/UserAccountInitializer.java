package com.sealmail.web.init;

import com.sealmail.app.security.PasswordEncoder;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import com.sealmail.infra.config.properties.AuthProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountInitializer {

    private final AuthProperties authProperties;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        for (AuthProperties.UserRecord record : authProperties.getUsers()) {
            if (record.getUsername() == null || record.getUsername().isBlank()) {
                continue;
            }
            if (userAccountRepository.existsByUsername(record.getUsername())) {
                continue;
            }

            String userId = record.getUserId() != null && !record.getUserId().isBlank()
                    ? record.getUserId()
                    : UUID.randomUUID().toString();
            String email = record.getEmail() != null && !record.getEmail().isBlank()
                    ? record.getEmail()
                    : record.getUsername() + "@sealmail.local";

            UserAccount userAccount = UserAccount.create(
                    userId,
                    record.getUsername(),
                    email,
                    passwordEncoder.encode(record.getPassword()),
                    new LinkedHashSet<>(record.getRoles()),
                    new LinkedHashSet<>(record.getManagedDomains())
            );
            userAccountRepository.save(userAccount);
            log.info("Seeded auth user into PostgreSQL: {}", userAccount.getUsername());
        }
    }
}
