package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DomainRoutingPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(DomainRoutingPolicyResolver.class);

    private final DomainConfigRepository domainConfigRepository;
    private final RelayPolicyPort relayPolicyPort;

    public DomainRoutingPolicyResolver(DomainConfigRepository domainConfigRepository,
                                       RelayPolicyPort relayPolicyPort) {
        this.domainConfigRepository = domainConfigRepository;
        this.relayPolicyPort = relayPolicyPort;
    }

    public Optional<DomainConfig> activeLocalDomain(String domain) {
        return activeDomain(domain)
                .filter(DomainConfig::isLocalDomain);
    }

    public Optional<DomainConfig> firstActiveLocalRecipientDomain(MailEnvelope envelope) {
        return envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .map(this::activeLocalDomain)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public List<String> unconfiguredExternalRecipientDomains(MailEnvelope envelope) {
        boolean allowUnconfiguredExternalRecipientDomains = allowUnconfiguredExternalRecipientDomains();
        return envelope.getRecipients().stream()
                .map(EmailAddress::getDomain)
                .distinct()
                .filter(domain -> recipientDomainNotAllowed(domain, allowUnconfiguredExternalRecipientDomains))
                .toList();
    }

    private Optional<DomainConfig> activeDomain(String domain) {
        return domainConfigRepository.findByDomain(domain)
                .filter(DomainConfig::isActive);
    }

    private boolean recipientDomainNotAllowed(String domain, boolean allowUnconfiguredExternalRecipientDomains) {
        Optional<DomainConfig> configured = domainConfigRepository.findByDomain(domain);
        if (configured.isPresent()) {
            return !configured.get().isActive();
        }
        return !allowUnconfiguredExternalRecipientDomains;
    }

    private boolean allowUnconfiguredExternalRecipientDomains() {
        if (relayPolicyPort == null) {
            return false;
        }
        try {
            return relayPolicyPort.getSettings().allowUnconfiguredExternalRecipientDomains();
        } catch (Exception e) {
            log.warn("Cannot read relay recipient domain scope setting, using secure default: {}", e.getMessage());
            return false;
        }
    }
}
