package com.sealmail.infra.mail.pipeline;

import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.policy.DomainConfig;
import com.sealmail.domain.policy.DomainConfigRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainRoutingPolicyResolverTest {

    @Test
    void findsFirstActiveLocalRecipientDomain() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        DomainConfig local = DomainConfig.create("domain-1", "example.com", true);
        DomainConfig remote = DomainConfig.create("domain-2", "remote.test", false);
        when(repository.findByDomain("remote.test")).thenReturn(Optional.of(remote));
        when(repository.findByDomain("example.com")).thenReturn(Optional.of(local));

        DomainRoutingPolicyResolver resolver = new DomainRoutingPolicyResolver(repository, relayPolicy(false));

        Optional<DomainConfig> domain = resolver.firstActiveLocalRecipientDomain(envelope(
                "alice@sender.test",
                "bob@remote.test",
                "carol@example.com"));

        assertTrue(domain.isPresent());
        assertEquals("example.com", domain.get().getDomain());
    }

    @Test
    void rejectsUnconfiguredRecipientDomainsByDefault() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.findByDomain("unknown.test")).thenReturn(Optional.empty());

        DomainRoutingPolicyResolver resolver = new DomainRoutingPolicyResolver(repository, relayPolicy(false));

        assertEquals(List.of("unknown.test"),
                resolver.unconfiguredExternalRecipientDomains(envelope("alice@example.com", "bob@unknown.test")));
    }

    @Test
    void allowsUnconfiguredRecipientDomainsWhenRelayPolicyAllowsIt() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        when(repository.findByDomain("unknown.test")).thenReturn(Optional.empty());

        DomainRoutingPolicyResolver resolver = new DomainRoutingPolicyResolver(repository, relayPolicy(true));

        assertTrue(resolver.unconfiguredExternalRecipientDomains(
                envelope("alice@example.com", "bob@unknown.test")).isEmpty());
    }

    @Test
    void rejectsInactiveConfiguredRecipientDomainsEvenWhenUnconfiguredDomainsAreAllowed() {
        DomainConfigRepository repository = mock(DomainConfigRepository.class);
        DomainConfig inactive = DomainConfig.create("domain-1", "partner.test", false);
        inactive.deactivate();
        when(repository.findByDomain("partner.test")).thenReturn(Optional.of(inactive));

        DomainRoutingPolicyResolver resolver = new DomainRoutingPolicyResolver(repository, relayPolicy(true));

        assertEquals(List.of("partner.test"),
                resolver.unconfiguredExternalRecipientDomains(envelope("alice@example.com", "bob@partner.test")));
    }

    private static MailEnvelope envelope(String sender, String... recipients) {
        return new MailEnvelope(
                "msg-" + UUID.randomUUID() + "@example.com",
                new EmailAddress(sender),
                java.util.Arrays.stream(recipients).map(EmailAddress::new).toList(),
                "127.0.0.1",
                "helo",
                Instant.now(),
                "body".getBytes());
    }

    private static RelayPolicyPort relayPolicy(boolean allowUnconfiguredExternalRecipientDomains) {
        return new RelayPolicyPort() {
            @Override
            public RelayPolicySettings getSettings() {
                return new RelayPolicySettings(
                        true,
                        "relay.example.test",
                        25,
                        null,
                        false,
                        null,
                        30000,
                        null,
                        allowUnconfiguredExternalRecipientDomains,
                        Instant.now());
            }

            @Override
            public RelayProbeSettings getProbeSettings() {
                return new RelayProbeSettings(true, "relay.example.test", 25, "", "", 30000);
            }

            @Override
            public RelayPolicySettings updateSettings(RelayPolicySettingsUpdate update) {
                return getSettings();
            }
        };
    }
}
