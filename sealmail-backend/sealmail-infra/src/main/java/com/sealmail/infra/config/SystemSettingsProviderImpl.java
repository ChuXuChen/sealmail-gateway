package com.sealmail.infra.config;

import com.sealmail.domain.system.GmEdgeSettings;
import com.sealmail.domain.system.SystemSettingsProvider;
import com.sealmail.domain.config.GmEdgePolicyPort;
import com.sealmail.infra.config.properties.CaProperties;
import com.sealmail.infra.config.properties.PostfixProperties;
import com.sealmail.infra.config.properties.SecurityProperties;
import com.sealmail.infra.config.properties.SmtpServerProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SystemSettingsProviderImpl implements SystemSettingsProvider {

    private final Environment environment;
    private final SmtpServerProperties smtpServerProperties;
    private final PostfixProperties postfixProperties;
    private final RelayPolicyService relayPolicyService;
    private final QuarantinePolicyService quarantinePolicyService;
    private final GmEdgePolicyService gmEdgePolicyService;
    private final SecurityProperties securityProperties;
    private final CaProperties caProperties;

    public SystemSettingsProviderImpl(Environment environment,
                                      SmtpServerProperties smtpServerProperties,
                                      PostfixProperties postfixProperties,
                                      RelayPolicyService relayPolicyService,
                                      QuarantinePolicyService quarantinePolicyService,
                                      GmEdgePolicyService gmEdgePolicyService,
                                      SecurityProperties securityProperties,
                                      CaProperties caProperties) {
        this.environment = environment;
        this.smtpServerProperties = smtpServerProperties;
        this.postfixProperties = postfixProperties;
        this.relayPolicyService = relayPolicyService;
        this.quarantinePolicyService = quarantinePolicyService;
        this.gmEdgePolicyService = gmEdgePolicyService;
        this.securityProperties = securityProperties;
        this.caProperties = caProperties;
    }

    @Override
    public SystemSettingsSnapshot snapshot() {
        return new SystemSettingsSnapshot(
                runtime(),
                smtpServer(),
                delivery(),
                gmEdge(),
                quarantinePolicy(),
                certificateValidation(),
                internalCa(),
                cryptoCapabilities()
        );
    }

    private RuntimeSettings runtime() {
        return new RuntimeSettings(
                environment.getProperty("spring.application.name", "sealmail-gateway"),
                List.of(environment.getActiveProfiles()),
                true,
                "PostgreSQL runtime policies + deployment secret references",
                Instant.now()
        );
    }

    private SmtpServerSettings smtpServer() {
        return new SmtpServerSettings(
                smtpServerProperties.getBindAddress(),
                smtpServerProperties.getPort(),
                smtpServerProperties.getMaxConnections(),
                smtpServerProperties.getMaxMessageSize()
        );
    }

    private DeliverySettings delivery() {
        com.sealmail.domain.config.RelayPolicyPort.RelayPolicySettings relay = relayPolicyService.getSettings();
        return new DeliverySettings(
                postfixProperties.isEnabled() ? "POSTFIX" : "DIRECT_RELAY",
                new PostfixSettings(
                        postfixProperties.isEnabled(),
                        postfixProperties.getHost(),
                        postfixProperties.getAfterFilterPort(),
                        postfixProperties.getOutboundPort(),
                        postfixProperties.getTimeout(),
                        emptyToNull(postfixProperties.getEnvelopeFrom())
                ),
                new RelaySettings(
                        relay.host(),
                        relay.port(),
                        relay.timeoutMs(),
                        hasText(relay.username()),
                        relay.passwordConfigured(),
                        relay.username(),
                        null
                )
        );
    }

    private GmEdgeSettings gmEdge() {
        GmEdgePolicyPort.GmEdgePolicySettings settings = gmEdgePolicyService.getSettings();
        return new GmEdgeSettings(
                settings.enabled(),
                new GmEdgeSettings.Inbound(
                        settings.inbound().enabled(),
                        settings.inbound().bindAddress(),
                        settings.inbound().startTlsPort(),
                        settings.inbound().implicitTlsPort(),
                        settings.inbound().backlog(),
                        settings.inbound().maxConnections()
                ),
                new GmEdgeSettings.Outbound(
                        settings.outbound().enabled(),
                        settings.outbound().bindAddress(),
                        settings.outbound().smartHostPort(),
                        settings.outbound().backlog(),
                        settings.outbound().maxConnections()
                ),
                new GmEdgeSettings.Postfix(
                        settings.postfix().host(),
                        settings.postfix().port()
                ),
                new GmEdgeSettings.Tls(
                        settings.tls().protocols(),
                        settings.tls().cipherSuites(),
                        settings.tls().keyStorePath(),
                        hasText(settings.tls().keyStorePath()),
                        settings.tls().keyStorePasswordConfigured(),
                        settings.tls().keyStorePasswordSecretRef(),
                        settings.tls().keyStoreType(),
                        settings.tls().trustStorePath(),
                        hasText(settings.tls().trustStorePath()),
                        settings.tls().trustStorePasswordConfigured(),
                        settings.tls().trustStorePasswordSecretRef(),
                        settings.tls().trustStoreType(),
                        settings.tls().trustAll()
                ),
                new GmEdgeSettings.Limits(
                        settings.limits().connectTimeoutMs(),
                        settings.limits().readTimeoutMs(),
                        settings.limits().maxMessageSizeBytes(),
                        settings.limits().maxLineLengthBytes(),
                        settings.limits().maxRecipients()
                ),
                settings.routes().stream()
                        .map(route -> new GmEdgeSettings.Route(
                                route.domainPattern(),
                                route.targetHost(),
                                route.targetPort(),
                                route.security()
                        ))
                        .toList()
        );
    }

    private QuarantinePolicySettings quarantinePolicy() {
        com.sealmail.domain.config.QuarantinePolicyPort.QuarantinePolicySettings settings =
                quarantinePolicyService.getSettings();
        return new QuarantinePolicySettings(
                settings.maxRetentionDays(),
                settings.notificationEnabled(),
                settings.releaseRequiresEncryption());
    }

    private CertificateValidationSettings certificateValidation() {
        return new CertificateValidationSettings(
                securityProperties.isCrlEnabled(),
                securityProperties.isOcspEnabled(),
                securityProperties.getOcspTimeout()
        );
    }

    private InternalCaSettings internalCa() {
        return new InternalCaSettings(
                caProperties.getCrlBaseUrl(),
                caProperties.getDefaultRootValidityDays(),
                caProperties.getDefaultIntermediateValidityDays(),
                caProperties.getDefaultEndEntityValidityDays()
        );
    }

    private List<CryptoCapability> cryptoCapabilities() {
        return List.of(
                new CryptoCapability("签名算法", List.of("SM3withSM2", "SHA256withRSA")),
                new CryptoCapability("内容加密", List.of("SM4-CBC", "AES-256-CBC")),
                new CryptoCapability("密钥交换", List.of("SM2 KeyAgreement", "RSA KeyTransport")),
                new CryptoCapability("哈希算法", List.of("SM3", "SHA-256"))
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }
}
