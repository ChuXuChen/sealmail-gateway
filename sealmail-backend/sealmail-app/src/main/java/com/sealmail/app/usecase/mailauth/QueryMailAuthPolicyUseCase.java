package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.response.MailAuthPolicyResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryMailAuthPolicyUseCase {

    private final MailAuthPolicyRepository repository;

    public QueryMailAuthPolicyUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MailAuthPolicyResponse execute(UserContext user) {
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以查看邮件认证策略");
        return MailAuthPolicyResponse.from(repository.findPolicy());
    }
}
