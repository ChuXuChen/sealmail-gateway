package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.response.MailAuthDnsProbeResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DkimKeyResolverPort;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthDnsProbePort;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.policy.DomainName;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProbeMailAuthDnsUseCase {

    private final MailAuthPolicyRepository repository;
    private final ObjectProvider<DkimKeyResolverPort> dkimKeyResolverProvider;
    private final ObjectProvider<MailAuthDnsProbePort> dnsProbePortProvider;

    public ProbeMailAuthDnsUseCase(MailAuthPolicyRepository repository,
                                   ObjectProvider<DkimKeyResolverPort> dkimKeyResolverProvider,
                                   ObjectProvider<MailAuthDnsProbePort> dnsProbePortProvider) {
        this.repository = repository;
        this.dkimKeyResolverProvider = dkimKeyResolverProvider;
        this.dnsProbePortProvider = dnsProbePortProvider;
    }

    @Transactional
    public List<MailAuthDnsProbeResponse> execute(String domain, UserContext user) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以探测邮件认证DNS记录");
        MailAuthDnsProbePort dnsProbePort = dnsProbePortProvider.stream()
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("DNS 探测适配器尚未启用"));
        DomainMailAuthPolicy policy = repository.findDomainPolicy(normalizedDomain)
                .orElseGet(() -> DomainMailAuthPolicy.defaults(normalizedDomain));
        Optional<String> dkimPublicKey = dkimKeyResolverProvider.stream()
                .findFirst()
                .flatMap(resolver -> resolver.resolvePublicKeyData(policy.dkimSigningPolicy().keyRef()));
        return dnsProbePort.probe(normalizedDomain, policy.dnsRecords(dkimPublicKey)).stream()
                .map(repository::saveDnsProbeResult)
                .map(MailAuthDnsProbeResponse::from)
                .toList();
    }
}
