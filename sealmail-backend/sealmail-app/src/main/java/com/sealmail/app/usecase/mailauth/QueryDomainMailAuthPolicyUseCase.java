package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.response.DomainMailAuthPolicyResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.policy.DomainName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryDomainMailAuthPolicyUseCase {

    private final MailAuthPolicyRepository repository;

    public QueryDomainMailAuthPolicyUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DomainMailAuthPolicyResponse execute(String domain, UserContext user) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以查看域名邮件认证策略");
        DomainMailAuthPolicy policy = repository.findDomainPolicy(normalizedDomain)
                .orElseGet(() -> DomainMailAuthPolicy.defaults(normalizedDomain));
        return DomainMailAuthPolicyResponse.from(policy);
    }
}
