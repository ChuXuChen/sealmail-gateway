package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.response.MailAuthDnsProbeResponse;
import com.sealmail.app.dto.response.MailAuthModernStatusResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryMailAuthStatusUseCase {

    private final MailAuthPolicyRepository repository;

    public QueryMailAuthStatusUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MailAuthModernStatusResponse execute(UserContext user) {
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以查看邮件认证状态");
        MailAuthPolicy policy = repository.findPolicy();
        List<DomainMailAuthPolicy> domainPolicies = repository.findDomainPolicies();
        long dkimEnabledCount = domainPolicies.stream()
                .filter(domainPolicy -> domainPolicy.dkimSigningPolicy().enabled())
                .count();
        return new MailAuthModernStatusResponse(
                policy.enabled(),
                policy.authservId(),
                policy.trustedProxyMode().name(),
                policy.failureDefaultAction().name(),
                domainPolicies.size(),
                dkimEnabledCount,
                repository.findLatestDnsProbeResults("*").stream()
                        .map(MailAuthDnsProbeResponse::from)
                        .toList());
    }
}
