package com.sealmail.app.usecase.mailauth;

import com.sealmail.app.dto.response.DnsRecordResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.mailauth.DkimKeyResolverPort;
import com.sealmail.domain.mailauth.DnsRecord;
import com.sealmail.domain.mailauth.DomainMailAuthPolicy;
import com.sealmail.domain.mailauth.MailAuthPolicyRepository;
import com.sealmail.domain.policy.DomainName;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GenerateMailAuthDnsRecordsUseCase {

    private final MailAuthPolicyRepository repository;
    private final ObjectProvider<DkimKeyResolverPort> dkimKeyResolverProvider;

    public GenerateMailAuthDnsRecordsUseCase(MailAuthPolicyRepository repository,
                                             ObjectProvider<DkimKeyResolverPort> dkimKeyResolverProvider) {
        this.repository = repository;
        this.dkimKeyResolverProvider = dkimKeyResolverProvider;
    }

    @Transactional(readOnly = true)
    public List<DnsRecordResponse> execute(String domain, UserContext user) {
        String normalizedDomain = DomainName.requireValid(domain);
        MailAuthUseCaseSupport.requireAdmin(user, "只有管理员可以查看邮件认证DNS记录");
        DomainMailAuthPolicy policy = repository.findDomainPolicy(normalizedDomain)
                .orElseGet(() -> DomainMailAuthPolicy.defaults(normalizedDomain));
        Optional<String> dkimPublicKey = dkimKeyResolverProvider.stream()
                .findFirst()
                .flatMap(resolver -> resolver.resolvePublicKeyData(policy.dkimSigningPolicy().keyRef()));
        return policy.dnsRecords(dkimPublicKey).stream()
                .map(this::toResponse)
                .toList();
    }

    private DnsRecordResponse toResponse(DnsRecord record) {
        return new DnsRecordResponse(record.type(), record.name(), record.value(), record.available());
    }
}
