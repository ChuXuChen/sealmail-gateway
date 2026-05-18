package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.mailsecurity.DeliveryRoute;
import com.sealmail.domain.mailsecurity.DeliveryRouteResolver;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DomainDeliveryRouteResolver implements DeliveryRouteResolver {

    private static final Logger log = LoggerFactory.getLogger(DomainDeliveryRouteResolver.class);

    private final DomainConfigRepository domainConfigRepository;

    public DomainDeliveryRouteResolver(DomainConfigRepository domainConfigRepository) {
        this.domainConfigRepository = domainConfigRepository;
    }

    @Override
    public Optional<DeliveryRoute> resolve(List<EmailAddress> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return Optional.empty();
        }

        List<String> recipientDomains = recipients.stream()
                .map(EmailAddress::getDomain)
                .distinct()
                .toList();
        List<DeliveryRoute> routes = new ArrayList<>();
        for (String recipientDomain : recipientDomains) {
            Optional<DeliveryRoute> route = domainConfigRepository.findByDomain(recipientDomain)
                    .filter(DomainConfig::isActive)
                    .filter(config -> !config.isLocalDomain())
                    .filter(DomainConfig::hasDeliveryRoute)
                    .map(config -> new DeliveryRoute(
                            config.getDomain(),
                            config.getDeliveryHost(),
                            config.getDeliveryTransportProfile(),
                            config.getDeliveryPort()));
            if (route.isEmpty()) {
                return Optional.empty();
            }
            routes.add(route.get());
        }

        DeliveryRoute first = routes.get(0);
        boolean sameTarget = routes.stream().allMatch(route -> sameTarget(first, route));
        if (!sameTarget) {
            log.warn("Skip domain delivery route because recipients target multiple delivery routes: {}",
                    recipientDomains);
            return Optional.empty();
        }
        return Optional.of(first);
    }

    private boolean sameTarget(DeliveryRoute first, DeliveryRoute candidate) {
        return first.port() == candidate.port()
                && first.host().equalsIgnoreCase(candidate.host())
                && first.transportProfile() == candidate.transportProfile();
    }
}
