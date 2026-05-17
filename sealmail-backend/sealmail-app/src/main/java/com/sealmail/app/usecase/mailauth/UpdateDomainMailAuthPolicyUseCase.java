package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.request.DomainMailAuthPolicyRequest;
import com.sealmail.app.dto.response.DomainMailAuthPolicyResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimSelector;
import com.sealmail.domain.mailauth.DkimSigningPolicy;
import com.sealmail.domain.mailauth.DmarcPublicationPolicy;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.mailauth.SpfPublicationPolicy;
import com.sealmail.domain.policy.DomainName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UpdateDomainMailAuthPolicyUseCase {

    private final MailAuthPolicyRepository repository;

    public UpdateDomainMailAuthPolicyUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DomainMailAuthPolicyResponse execute(String domain,
                                                DomainMailAuthPolicyRequest request,
                                                UserContext user) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以更新域名邮件认证策略");
        if (request == null) {
            throw BusinessException.badRequest("域名邮件认证策略请求不能为空");
        }
        try {
            DomainMailAuthPolicy current = repository.findDomainPolicy(normalizedDomain)
                    .orElseGet(() -> DomainMailAuthPolicy.defaults(normalizedDomain));
            DomainMailAuthPolicy updated = new DomainMailAuthPolicy(
                    normalizedDomain,
                    request.enabled() != null ? request.enabled() : current.enabled(),
                    dkimPolicy(current, request),
                    spfPolicy(current, request),
                    dmarcPolicy(current, request),
                    current.createdAt(),
                    Instant.now(),
                    current.version());
            return DomainMailAuthPolicyResponse.from(repository.saveDomainPolicy(updated));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        }
    }

    private DkimSigningPolicy dkimPolicy(DomainMailAuthPolicy current, DomainMailAuthPolicyRequest request) {
        DkimSigningPolicy existing = current.dkimSigningPolicy();
        return new DkimSigningPolicy(
                request.dkimSigningEnabled() != null ? request.dkimSigningEnabled() : existing.enabled(),
                request.dkimSelector() != null ? new DkimSelector(request.dkimSelector()) : existing.selector(),
                dkimKeyRef(existing, request),
                request.dkimSignedHeaders() != null ? request.dkimSignedHeaders() : existing.signedHeaders());
    }

    private DkimKeyRef dkimKeyRef(DkimSigningPolicy existing, DomainMailAuthPolicyRequest request) {
        if (request.dkimKeySecretRef() != null) {
            return new DkimKeyRef(request.dkimKeySecretRef(), null);
        }
        if (request.dkimKeyPath() != null) {
            return new DkimKeyRef(null, request.dkimKeyPath());
        }
        return existing.keyRef();
    }

    private SpfPublicationPolicy spfPolicy(DomainMailAuthPolicy current, DomainMailAuthPolicyRequest request) {
        SpfPublicationPolicy existing = current.spfPublicationPolicy();
        return new SpfPublicationPolicy(
                request.spfPublishEnabled() != null ? request.spfPublishEnabled() : existing.enabled(),
                request.spfUseA() != null ? request.spfUseA() : existing.useA(),
                request.spfUseMx() != null ? request.spfUseMx() : existing.useMx(),
                request.spfIp4() != null ? request.spfIp4() : existing.ip4(),
                request.spfIp6() != null ? request.spfIp6() : existing.ip6(),
                request.spfIncludes() != null ? request.spfIncludes() : existing.includes(),
                request.spfAllPolicy() != null ? request.spfAllPolicy() : existing.allPolicy());
    }

    private DmarcPublicationPolicy dmarcPolicy(DomainMailAuthPolicy current,
                                               DomainMailAuthPolicyRequest request) {
        DmarcPublicationPolicy existing = current.dmarcPublicationPolicy();
        return new DmarcPublicationPolicy(
                request.dmarcPublishEnabled() != null ? request.dmarcPublishEnabled() : existing.enabled(),
                MailAuthUseCaseSupport.dmarcPolicy(request.dmarcPolicy(), existing.policy()),
                MailAuthUseCaseSupport.dmarcPolicy(request.dmarcSubdomainPolicy(), existing.subdomainPolicy()),
                MailAuthUseCaseSupport.alignmentMode(request.dmarcAdkim(), existing.dkimAlignment()),
                MailAuthUseCaseSupport.alignmentMode(request.dmarcAspf(), existing.spfAlignment()),
                request.dmarcPct() != null ? request.dmarcPct() : existing.pct(),
                request.dmarcRua() != null ? request.dmarcRua() : existing.rua(),
                request.dmarcRuf() != null ? request.dmarcRuf() : existing.ruf());
    }
}
