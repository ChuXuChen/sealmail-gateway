package com.sealmail.app.usecase.auth;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.CurrentUserResolver;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.security.AuthTokenPort;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ResolveCurrentUserUseCase {

    private final UserAccountRepository userAccountRepository;
    private final CurrentUserResolver currentUserResolver;
    private final AuthTokenPort authTokenPort;

    public ResolveCurrentUserUseCase(UserAccountRepository userAccountRepository,
                                     CurrentUserResolver currentUserResolver,
                                     AuthTokenPort authTokenPort) {
        this.userAccountRepository = userAccountRepository;
        this.currentUserResolver = currentUserResolver;
        this.authTokenPort = authTokenPort;
    }

    @Transactional(readOnly = true)
    public UserContext requireCurrentUser(UserContext user) {
        UserAccount userAccount = requireCurrentUserAccount(user);
        return currentUserResolver.toUserContext(userAccount);
    }

    @Transactional(readOnly = true)
    public UserContext loadUsableUserContext(String userId, Instant tokenIssuedAt) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        UserAccount userAccount = userAccountRepository.findById(userId).orElse(null);
        if (!isTokenUsable(userAccount, tokenIssuedAt)) {
            return null;
        }
        return currentUserResolver.toUserContext(userAccount);
    }

    @Transactional(readOnly = true)
    public UserContext loadUsableUserContextFromToken(String token) {
        return authTokenPort.read(token)
                .map(subject -> loadUsableUserContext(subject.userId(), subject.issuedAt()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UserAccount requireCurrentUserAccount(UserContext user) {
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw BusinessException.unauthorized("未登录");
        }
        return userAccountRepository.findById(user.getUserId())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
    }

    private boolean isTokenUsable(UserAccount userAccount, Instant tokenIssuedAt) {
        if (userAccount == null || !userAccount.isActive() || !userAccount.canAuthenticate()) {
            return false;
        }
        Instant tokenInvalidBefore = userAccount.getTokenInvalidBefore();
        if (tokenInvalidBefore == null) {
            return true;
        }
        return tokenIssuedAt != null && !tokenIssuedAt.isBefore(tokenInvalidBefore);
    }
}
