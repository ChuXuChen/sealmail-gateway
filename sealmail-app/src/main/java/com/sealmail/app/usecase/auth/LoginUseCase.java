package com.sealmail.app.usecase.auth;

import com.sealmail.app.dto.response.LoginResponse;
import com.sealmail.domain.security.AuthTokenPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LoginUseCase {

    private final LocalAuthService localAuthService;
    private final AuthTokenPort authTokenPort;

    public LoginUseCase(LocalAuthService localAuthService,
                        AuthTokenPort authTokenPort) {
        this.localAuthService = localAuthService;
        this.authTokenPort = authTokenPort;
    }

    @Transactional
    public LoginResponse execute(String username, String password, String ipAddress) {
        LocalAuthService.AuthenticatedUser user =
                localAuthService.authenticate(username, password, ipAddress);
        AuthTokenPort.IssuedToken token = authTokenPort.issue(new AuthTokenPort.TokenSubject(
                user.userId(),
                user.username(),
                user.email(),
                user.roles(),
                user.managedDomains(),
                Instant.now()));

        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                new LoginResponse.UserInfo(
                        user.userId(),
                        user.username(),
                        user.email(),
                        user.roles(),
                        user.managedDomains(),
                        null,
                        null));
    }
}
