package com.sealmail.app.usecase.bootstrap;

import com.sealmail.domain.security.PasswordEncoder;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class SeedUserAccountUseCase {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedUserAccountUseCase(UserAccountRepository userAccountRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean seed(UserSeedCommand command) {
        if (command.username() == null || command.username().isBlank()) {
            return false;
        }
        if (command.password() == null || command.password().isBlank()) {
            return false;
        }
        if (userAccountRepository.existsByUsername(command.username())) {
            return false;
        }

        String userId = command.userId() != null && !command.userId().isBlank()
                ? command.userId()
                : UUID.randomUUID().toString();
        String email = command.email() != null && !command.email().isBlank()
                ? command.email()
                : command.username() + "@sealmail.local";
        if (userAccountRepository.existsByEmail(email)) {
            return false;
        }

        UserAccount userAccount = UserAccount.create(
                userId,
                command.username(),
                email,
                passwordEncoder.encode(command.password()),
                new LinkedHashSet<>(command.roles()),
                new LinkedHashSet<>(command.managedDomains())
        );
        userAccountRepository.save(userAccount);
        return true;
    }

    public record UserSeedCommand(
            String userId,
            String username,
            String email,
            String password,
            List<String> roles,
            List<String> managedDomains
    ) {
        public UserSeedCommand {
            roles = roles == null ? List.of() : List.copyOf(roles);
            managedDomains = managedDomains == null ? List.of() : List.copyOf(managedDomains);
        }
    }
}
