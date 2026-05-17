package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.request.RotateDkimSelectorRequest;
import com.sealmail.app.dto.response.DomainMailAuthPolicyResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DkimKeyRef;
import com.sealmail.domain.mailauth.DkimSelector;
import com.sealmail.domain.mailauth.DkimSigningPolicy;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.policy.DomainName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RotateDkimSelectorUseCase {

    private final MailAuthPolicyRepository repository;

    public RotateDkimSelectorUseCase(MailAuthPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DomainMailAuthPolicyResponse execute(String domain,
                                                RotateDkimSelectorRequest request,
                                                UserContext user) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以轮换 DKIM selector");
        if (request == null || request.selector() == null || request.selector().isBlank()) {
            throw BusinessException.badRequest("新的 DKIM selector 不能为空");
        }
        try {
            DomainMailAuthPolicy current = repository.findDomainPolicy(normalizedDomain)
                    .orElseGet(() -> DomainMailAuthPolicy.defaults(normalizedDomain));
            DkimSigningPolicy existing = current.dkimSigningPolicy();
            DkimSigningPolicy rotated = new DkimSigningPolicy(
                    true,
                    new DkimSelector(request.selector()),
                    dkimKeyRef(existing, request),
                    request.signedHeaders() != null ? request.signedHeaders() : existing.signedHeaders());
            return DomainMailAuthPolicyResponse.from(repository.saveDomainPolicy(current.withDkimSigningPolicy(rotated)));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        }
    }

    private DkimKeyRef dkimKeyRef(DkimSigningPolicy existing, RotateDkimSelectorRequest request) {
        if (request.keySecretRef() != null) {
            return new DkimKeyRef(request.keySecretRef(), null);
        }
        if (request.keyPath() != null) {
            return new DkimKeyRef(null, request.keyPath());
        }
        return existing.keyRef();
    }
}
