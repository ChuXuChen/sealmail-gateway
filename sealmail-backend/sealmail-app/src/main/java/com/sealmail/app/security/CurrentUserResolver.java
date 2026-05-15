package com.sealmail.app.security;

import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserResolver(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public UserContext toUserContext(UserAccount userAccount) {
        return UserContext.builder()
                .userId(userAccount.getId())
                .username(userAccount.getUsername())
                .email(userAccount.getEmail())
                .roles(userAccount.getRoles())
                .managedDomains(userAccount.getManagedDomains())
                .build();
    }

    public UserContext loadUserContext(String userId) {
        return userAccountRepository.findById(userId)
                .filter(UserAccount::isActive)
                .map(this::toUserContext)
                .orElse(null);
    }
}
