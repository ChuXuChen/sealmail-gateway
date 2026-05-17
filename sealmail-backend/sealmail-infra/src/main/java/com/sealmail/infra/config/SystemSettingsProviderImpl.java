package com.sealmail.infra.config;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;
import com.sealmail.domain.system.SystemSettingsProvider;
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
    private final SecurityProperties securityProperties;
    private final CaProperties caProperties;

    public SystemSettingsProviderImpl(Environment environment,
                                      SmtpServerProperties smtpServerProperties,
                                      PostfixProperties postfixProperties,
                                      RelayPolicyService relayPolicyService,
                                      QuarantinePolicyService quarantinePolicyService,
                                      SecurityProperties securityProperties,
                                      CaProperties caProperties) {
        this.environment = environment;
        this.smtpServerProperties = smtpServerProperties;
        this.postfixProperties = postfixProperties;
        this.relayPolicyService = relayPolicyService;
        this.quarantinePolicyService = quarantinePolicyService;
        this.securityProperties = securityProperties;
        this.caProperties = caProperties;
    }

    @Override
    public SystemSettingsSnapshot snapshot() {
        return new SystemSettingsSnapshot(
                runtime(),
                smtpServer(),
                delivery(),
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
                "PostgreSQL runtime policies + deployment-level YAML references",
                Instant.now()
        );
    }

    private SmtpServerSettings smtpServer() {
        return new SmtpServerSettings(
                smtpServerProperties.getBindAddress(),
                smtpServerProperties.getPort(),
                smtpServerProperties.getMaxConnections(),
                smtpServerProperties.getMaxMessageSize(),
                new TlsSettings(
                        smtpServerProperties.isEnableStartTls(),
                        smtpServerProperties.isRequireTls(),
                        hasText(smtpServerProperties.getKeystorePath()),
                        hasText(smtpServerProperties.getCertificatePath())
                                && hasText(smtpServerProperties.getPrivateKeyPath()),
                        emptyToNull(smtpServerProperties.getKeyAlias())
                )
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
                        postfixProperties.isUseTls(),
                        SmtpTransportSecurity.fromLegacyUseTls(
                                postfixProperties.isUseTls(),
                                postfixProperties.getOutboundPort()),
                        postfixProperties.getTimeout(),
                        emptyToNull(postfixProperties.getEnvelopeFrom())
                ),
                new RelaySettings(
                        relay.host(),
                        relay.port(),
                        relay.useTls(),
                        relay.transportSecurity(),
                        relay.timeoutMs(),
                        hasText(relay.username()),
                        relay.passwordConfigured(),
                        relay.username(),
                        null
                )
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
