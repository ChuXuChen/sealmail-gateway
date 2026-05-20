package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.response.SystemSettingsResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.system.SystemSettingsProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuerySystemSettingsUseCase {

    private final SystemSettingsProvider systemSettingsProvider;

    public QuerySystemSettingsUseCase(SystemSettingsProvider systemSettingsProvider) {
        this.systemSettingsProvider = systemSettingsProvider;
    }

    @PreAuthorize("@appPermissionAuthorizer.canViewSystemSettings(#user)")
    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettings(UserContext user) {
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
                        snapshot.smtpServer().maxMessageSizeBytes()
                ),
                new SystemSettingsResponse.DeliveryResponse(
                        snapshot.delivery().mode(),
                        new SystemSettingsResponse.PostfixResponse(
                                snapshot.delivery().postfix().enabled(),
                                snapshot.delivery().postfix().host(),
                                snapshot.delivery().postfix().afterFilterPort(),
                                snapshot.delivery().postfix().outboundPort(),
                                snapshot.delivery().postfix().timeoutMs(),
                                snapshot.delivery().postfix().envelopeFrom()
                        ),
                        new SystemSettingsResponse.RelayResponse(
                                snapshot.delivery().directRelay().host(),
                                snapshot.delivery().directRelay().port(),
                                snapshot.delivery().directRelay().timeoutMs(),
                                snapshot.delivery().directRelay().usernameConfigured(),
                                snapshot.delivery().directRelay().passwordConfigured()
                        )
                ),
                new SystemSettingsResponse.GmEdgeResponse(
                        snapshot.gmEdge().enabled(),
                        new SystemSettingsResponse.GmEdgeInboundResponse(
                                snapshot.gmEdge().inbound().enabled(),
                                snapshot.gmEdge().inbound().bindAddress(),
                                snapshot.gmEdge().inbound().startTlsPort(),
                                snapshot.gmEdge().inbound().implicitTlsPort(),
                                snapshot.gmEdge().inbound().backlog(),
                                snapshot.gmEdge().inbound().maxConnections()
                        ),
                        new SystemSettingsResponse.GmEdgeOutboundResponse(
                                snapshot.gmEdge().outbound().enabled(),
                                snapshot.gmEdge().outbound().bindAddress(),
                                snapshot.gmEdge().outbound().smartHostPort(),
                                snapshot.gmEdge().outbound().backlog(),
                                snapshot.gmEdge().outbound().maxConnections()
                        ),
                        new SystemSettingsResponse.GmEdgePostfixResponse(
                                snapshot.gmEdge().postfix().host(),
                                snapshot.gmEdge().postfix().port()
                        ),
                        new SystemSettingsResponse.GmEdgeTlsResponse(
                                snapshot.gmEdge().tls().protocols(),
                                snapshot.gmEdge().tls().cipherSuites(),
                                snapshot.gmEdge().tls().keyStorePath(),
                                snapshot.gmEdge().tls().keyStoreConfigured(),
                                snapshot.gmEdge().tls().keyStorePasswordConfigured(),
                                snapshot.gmEdge().tls().keyStorePasswordSecretRef(),
                                snapshot.gmEdge().tls().keyStoreType(),
                                snapshot.gmEdge().tls().trustStorePath(),
                                snapshot.gmEdge().tls().trustStoreConfigured(),
                                snapshot.gmEdge().tls().trustStorePasswordConfigured(),
                                snapshot.gmEdge().tls().trustStorePasswordSecretRef(),
                                snapshot.gmEdge().tls().trustStoreType(),
                                snapshot.gmEdge().tls().trustAll()
                        ),
                        new SystemSettingsResponse.GmEdgeLimitsResponse(
                                snapshot.gmEdge().limits().connectTimeoutMs(),
                                snapshot.gmEdge().limits().readTimeoutMs(),
                                snapshot.gmEdge().limits().maxMessageSizeBytes(),
                                snapshot.gmEdge().limits().maxLineLengthBytes(),
                                snapshot.gmEdge().limits().maxRecipients()
                        ),
                        snapshot.gmEdge().routes().stream()
                                .map(route -> new SystemSettingsResponse.GmEdgeRouteResponse(
                                        route.domainPattern(),
                                        route.targetHost(),
                                        route.targetPort(),
                                        route.security()
                                ))
                                .toList()
                ),
                new SystemSettingsResponse.SmimeSuitePolicyResponse(
                        snapshot.smimeSuitePolicy().defaultStandardSuite(),
                        snapshot.smimeSuitePolicy().defaultGmSuite(),
                        snapshot.smimeSuitePolicy().standardSuites().stream()
                                .map(option -> new SystemSettingsResponse.SmimeSuiteOptionResponse(
                                        option.id(),
                                        option.displayName(),
                                        option.profile()))
                                .toList(),
                        snapshot.smimeSuitePolicy().gmSuites().stream()
                                .map(option -> new SystemSettingsResponse.SmimeSuiteOptionResponse(
                                        option.id(),
                                        option.displayName(),
                                        option.profile()))
                                .toList()
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
