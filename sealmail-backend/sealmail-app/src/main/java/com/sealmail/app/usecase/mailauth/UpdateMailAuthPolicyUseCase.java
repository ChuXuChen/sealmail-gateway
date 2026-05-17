package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.request.MailAuthPolicyRequest;
import com.sealmail.app.dto.response.MailAuthPolicyResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.MailAuthFailureAction;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.TrustedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UpdateMailAuthPolicyUseCase {

    private final MailAuthPolicyRepository repository;

    public UpdateMailAuthPolicyUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MailAuthPolicyResponse execute(MailAuthPolicyRequest request, UserContext user) {
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以更新邮件认证策略");
        if (request == null) {
            throw BusinessException.badRequest("邮件认证策略请求不能为空");
        }
        MailAuthPolicy current = repository.findPolicy();
        MailAuthPolicy updated = new MailAuthPolicy(
                current.id(),
                request.enabled() != null ? request.enabled() : current.enabled(),
                request.authservId() != null ? request.authservId() : current.authservId(),
                MailAuthUseCaseSupport.trustedProxyMode(request.trustedProxyMode(), current.trustedProxyMode()),
                MailAuthUseCaseSupport.failureAction(request.failureDefaultAction(), current.failureDefaultAction()),
                current.createdAt(),
                Instant.now(),
                current.version());
        return MailAuthPolicyResponse.from(repository.savePolicy(updated));
    }
}
