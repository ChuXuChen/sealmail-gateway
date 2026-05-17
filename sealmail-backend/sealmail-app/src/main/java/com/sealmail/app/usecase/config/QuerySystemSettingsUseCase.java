package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.response.SystemSettingsResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.system.SystemSettingsProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuerySystemSettingsUseCase {

    private final SystemSettingsProvider systemSettingsProvider;

    public QuerySystemSettingsUseCase(SystemSettingsProvider systemSettingsProvider) {
        this.systemSettingsProvider = systemSettingsProvider;
    }

    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettings(UserContext user) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden("只有管理员可以查看系统设置");
        }
        return toResponse(systemSettingsProvider.snapshot());
    }

    private SystemSettingsResponse toResponse(SystemSettingsProvider.SystemSettingsSnapshot snapshot) {
        return new SystemSettingsResponse(
                new SystemSettingsResponse.RuntimeResponse(
                        snapshot.runtime().applicationName(),
                        snapshot.runtime().activeProfiles(),
                        snapshot.runtime().onlineEditingSupported(),
                        snapshot.runtime().configSource(),
                        snapshot.runtime().generatedAt()
                ),
                new SystemSettingsResponse.SmtpServerResponse(
                        snapshot.smtpServer().bindAddress(),
                        snapshot.smtpServer().port(),
                        snapshot.smtpServer().maxConnections(),
                        snapshot.smtpServer().maxMessageSizeBytes(),
                        new SystemSettingsResponse.TlsResponse(
                                snapshot.smtpServer().tls().startTlsEnabled(),
                                snapshot.smtpServer().tls().tlsRequired(),
                                snapshot.smtpServer().tls().keystoreConfigured(),
                                snapshot.smtpServer().tls().pemConfigured(),
                                snapshot.smtpServer().tls().keyAlias(),
                                snapshot.smtpServer().tls().engine(),
                                snapshot.smtpServer().tls().provider(),
                                snapshot.smtpServer().tls().protocol(),
                                snapshot.smtpServer().tls().enabledProtocols(),
                                snapshot.smtpServer().tls().enabledCipherSuites()
                        )
                ),
                new SystemSettingsResponse.DeliveryResponse(
                        snapshot.delivery().mode(),
                        new SystemSettingsResponse.PostfixResponse(
                                snapshot.delivery().postfix().enabled(),
                                snapshot.delivery().postfix().host(),
                                snapshot.delivery().postfix().afterFilterPort(),
                                snapshot.delivery().postfix().outboundPort(),
                                snapshot.delivery().postfix().useTls(),
                                snapshot.delivery().postfix().transportSecurity(),
                                snapshot.delivery().postfix().timeoutMs(),
                                snapshot.delivery().postfix().envelopeFrom()
                        ),
                        new SystemSettingsResponse.RelayResponse(
                                snapshot.delivery().directRelay().host(),
                                snapshot.delivery().directRelay().port(),
                                snapshot.delivery().directRelay().useTls(),
                                snapshot.delivery().directRelay().transportSecurity(),
                                snapshot.delivery().directRelay().timeoutMs(),
                                snapshot.delivery().directRelay().usernameConfigured(),
                                snapshot.delivery().directRelay().passwordConfigured()
                        )
                ),
                new SystemSettingsResponse.QuarantinePolicyResponse(
                        snapshot.quarantinePolicy().maxRetentionDays(),
                        snapshot.quarantinePolicy().notificationEnabled(),
                        snapshot.quarantinePolicy().releaseRequiresEncryption()
                ),
                new SystemSettingsResponse.CertificateValidationResponse(
                        snapshot.certificateValidation().crlEnabled(),
                        snapshot.certificateValidation().ocspEnabled(),
                        snapshot.certificateValidation().ocspTimeoutMs()
                ),
                new SystemSettingsResponse.InternalCaResponse(
                        snapshot.internalCa().crlBaseUrl(),
                        snapshot.internalCa().defaultRootValidityDays(),
                        snapshot.internalCa().defaultIntermediateValidityDays(),
                        snapshot.internalCa().defaultEndEntityValidityDays()
                ),
                snapshot.cryptoCapabilities().stream()
                        .map(capability -> new SystemSettingsResponse.CryptoCapabilityResponse(
                                capability.category(),
                                capability.algorithms()))
                        .toList()
        );
    }
}
