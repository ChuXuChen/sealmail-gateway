package com.sealmail.app.usecase.auth;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.domain.audit.AuditService;
import com.sealmail.domain.security.PasswordEncoder;
import com.sealmail.domain.user.UserAccount;
import com.sealmail.domain.user.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocalAuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public LocalAuthService(UserAccountRepository userAccountRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public AuthenticatedUser authenticate(String username, String password, String ipAddress) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> loginFailed(username, ipAddress, "用户名或密码错误"));

        if (!user.isActive()) {
            auditService.recordLoginFailed(username, ipAddress, "账号已禁用");
            throw BusinessException.unauthorized("账号已禁用");
        }
        if (!user.canAuthenticate()) {
            auditService.recordLoginFailed(username, ipAddress, "账号已锁定");
            throw BusinessException.unauthorized("账号已锁定");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.markLoginFailed(5);
            userAccountRepository.save(user);
            auditService.recordLoginFailed(username, ipAddress, "用户名或密码错误");
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        user.markLoginSuccess(ipAddress);
        userAccountRepository.save(user);
        auditService.recordLogin(user.getId(), user.getUsername(), ipAddress);
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream().toList(),
                user.getManagedDomains().stream().toList()
        );
    }

    private BusinessException loginFailed(String username, String ipAddress, String message) {
        auditService.recordLoginFailed(username, ipAddress, message);
        return BusinessException.unauthorized(message);
    }

    public record AuthenticatedUser(
            String userId,
            String username,
            String email,
            List<String> roles,
            List<String> managedDomains
    ) {
    }
}
