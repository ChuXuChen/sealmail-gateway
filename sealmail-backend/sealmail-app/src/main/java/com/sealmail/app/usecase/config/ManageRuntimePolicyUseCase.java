package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.request.QuarantinePolicyRequest;
import com.sealmail.app.dto.request.GmEdgePolicyRequest;
import com.sealmail.app.dto.request.RelayPolicyRequest;
import com.sealmail.app.dto.request.SmimeSuitePolicyRequest;
import com.sealmail.app.dto.response.GmEdgePolicyResponse;
import com.sealmail.app.dto.response.QuarantinePolicyResponse;
import com.sealmail.app.dto.response.RelayPolicyResponse;
import com.sealmail.app.dto.response.SmimeSuitePolicyResponse;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.config.GmEdgePolicyPort;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.config.RelayPolicyPort;
import com.sealmail.domain.config.SmimeSuitePolicyPort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ManageRuntimePolicyUseCase {

    private final RelayPolicyPort relayPolicyPort;
    private final QuarantinePolicyPort quarantinePolicyPort;
    private final GmEdgePolicyPort gmEdgePolicyPort;
    private final SmimeSuitePolicyPort smimeSuitePolicyPort;

    public ManageRuntimePolicyUseCase(RelayPolicyPort relayPolicyPort,
                                      QuarantinePolicyPort quarantinePolicyPort,
                                      GmEdgePolicyPort gmEdgePolicyPort,
                                      SmimeSuitePolicyPort smimeSuitePolicyPort) {
        this.relayPolicyPort = relayPolicyPort;
        this.quarantinePolicyPort = quarantinePolicyPort;
        this.gmEdgePolicyPort = gmEdgePolicyPort;
        this.smimeSuitePolicyPort = smimeSuitePolicyPort;
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional(readOnly = true)
    public RelayPolicyResponse getRelayPolicy(UserContext user) {
        return toRelayResponse(relayPolicyPort.getSettings());
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional
    public RelayPolicyResponse updateRelayPolicy(RelayPolicyRequest request, UserContext user) {
        return toRelayResponse(relayPolicyPort.updateSettings(toRelayUpdate(request)));
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional(readOnly = true)
    public QuarantinePolicyResponse getQuarantinePolicy(UserContext user) {
        return toQuarantineResponse(quarantinePolicyPort.getSettings());
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional
    public QuarantinePolicyResponse updateQuarantinePolicy(QuarantinePolicyRequest request, UserContext user) {
        return toQuarantineResponse(quarantinePolicyPort.updateSettings(toQuarantineUpdate(request)));
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional(readOnly = true)
    public GmEdgePolicyResponse getGmEdgePolicy(UserContext user) {
        return toGmEdgeResponse(gmEdgePolicyPort.getSettings());
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional
    public GmEdgePolicyResponse updateGmEdgePolicy(GmEdgePolicyRequest request, UserContext user) {
        return toGmEdgeResponse(gmEdgePolicyPort.updateSettings(toGmEdgeUpdate(request)));
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional(readOnly = true)
    public SmimeSuitePolicyResponse getSmimeSuitePolicy(UserContext user) {
        return toSmimeSuiteResponse(smimeSuitePolicyPort.getSettings());
    }

    @PreAuthorize("@appPermissionAuthorizer.canManageRuntimePolicy(#user)")
    @Transactional
    public SmimeSuitePolicyResponse updateSmimeSuitePolicy(SmimeSuitePolicyRequest request, UserContext user) {
        return toSmimeSuiteResponse(smimeSuitePolicyPort.updateSettings(toSmimeSuiteUpdate(request)));
    }

    private RelayPolicyPort.RelayPolicySettingsUpdate toRelayUpdate(RelayPolicyRequest request) {
        if (request == null) {
            return new RelayPolicyPort.RelayPolicySettingsUpdate(null, null, null, null, null, null, null, null, null);
        }
        return new RelayPolicyPort.RelayPolicySettingsUpdate(
                request.enabled(),
                request.host(),
                request.port(),
                request.username(),
                request.passwordSecretRef(),
                request.clearPasswordSecretRef(),
                request.timeoutMs(),
                request.envelopeFrom(),
                request.allowUnconfiguredExternalRecipientDomains());
    }

    private QuarantinePolicyPort.QuarantinePolicySettingsUpdate toQuarantineUpdate(QuarantinePolicyRequest request) {
        if (request == null) {
            return new QuarantinePolicyPort.QuarantinePolicySettingsUpdate(null, null, null);
        }
        return new QuarantinePolicyPort.QuarantinePolicySettingsUpdate(
                request.maxRetentionDays(),
                request.notificationEnabled(),
                request.releaseRequiresEncryption());
    }

    private GmEdgePolicyPort.GmEdgePolicySettingsUpdate toGmEdgeUpdate(GmEdgePolicyRequest request) {
        if (request == null) {
            return new GmEdgePolicyPort.GmEdgePolicySettingsUpdate(
                    null, null, null, null, null, null, null);
        }
        return new GmEdgePolicyPort.GmEdgePolicySettingsUpdate(
                request.enabled(),
                request.inbound() == null ? null : new GmEdgePolicyPort.InboundSettingsUpdate(
                        request.inbound().enabled(),
                        request.inbound().bindAddress(),
                        request.inbound().startTlsPort(),
                        request.inbound().implicitTlsPort(),
                        request.inbound().backlog(),
                        request.inbound().maxConnections()),
                request.outbound() == null ? null : new GmEdgePolicyPort.OutboundSettingsUpdate(
                        request.outbound().enabled(),
                        request.outbound().bindAddress(),
                        request.outbound().smartHostPort(),
                        request.outbound().backlog(),
                        request.outbound().maxConnections()),
                request.postfix() == null ? null : new GmEdgePolicyPort.PostfixSettingsUpdate(
                        request.postfix().host(),
                        request.postfix().port()),
                request.tls() == null ? null : new GmEdgePolicyPort.TlsSettingsUpdate(
                        request.tls().protocols(),
                        request.tls().cipherSuites(),
                        request.tls().keyStorePath(),
                        request.tls().keyStorePasswordSecretRef(),
                        request.tls().clearKeyStorePasswordSecretRef(),
                        request.tls().keyStoreType(),
                        request.tls().trustStorePath(),
                        request.tls().trustStorePasswordSecretRef(),
                        request.tls().clearTrustStorePasswordSecretRef(),
                        request.tls().trustStoreType(),
                        request.tls().trustAll()),
                request.limits() == null ? null : new GmEdgePolicyPort.LimitsSettingsUpdate(
                        request.limits().connectTimeoutMs(),
                        request.limits().readTimeoutMs(),
                        request.limits().maxMessageSizeBytes(),
                        request.limits().maxLineLengthBytes(),
                        request.limits().maxRecipients()),
                request.routes() == null ? null : request.routes().stream()
                        .map(route -> new GmEdgePolicyPort.RouteSettings(
                                route.domainPattern(),
                                route.targetHost(),
                                route.targetPort() == null ? 0 : route.targetPort(),
                                route.security()))
                        .toList());
    }

    private SmimeSuitePolicyPort.SmimeSuitePolicySettingsUpdate toSmimeSuiteUpdate(SmimeSuitePolicyRequest request) {
        if (request == null) {
            return new SmimeSuitePolicyPort.SmimeSuitePolicySettingsUpdate(null, null);
        }
        return new SmimeSuitePolicyPort.SmimeSuitePolicySettingsUpdate(
                request.defaultStandardSuite(),
                request.defaultGmSuite());
    }

    private RelayPolicyResponse toRelayResponse(RelayPolicyPort.RelayPolicySettings settings) {
        return new RelayPolicyResponse(
                settings.enabled(),
                settings.host(),
                settings.port(),
                settings.username(),
                settings.passwordConfigured(),
                settings.passwordSecretRef(),
                settings.timeoutMs(),
                settings.envelopeFrom(),
                settings.allowUnconfiguredExternalRecipientDomains(),
                settings.updatedAt());
    }

    private QuarantinePolicyResponse toQuarantineResponse(QuarantinePolicyPort.QuarantinePolicySettings settings) {
        return new QuarantinePolicyResponse(
                settings.maxRetentionDays(),
                settings.notificationEnabled(),
                settings.releaseRequiresEncryption(),
                settings.updatedAt());
    }

    private GmEdgePolicyResponse toGmEdgeResponse(GmEdgePolicyPort.GmEdgePolicySettings settings) {
        return new GmEdgePolicyResponse(
                settings.enabled(),
                new GmEdgePolicyResponse.InboundResponse(
                        settings.inbound().enabled(),
                        settings.inbound().bindAddress(),
                        settings.inbound().startTlsPort(),
                        settings.inbound().implicitTlsPort(),
                        settings.inbound().backlog(),
                        settings.inbound().maxConnections()),
                new GmEdgePolicyResponse.OutboundResponse(
                        settings.outbound().enabled(),
                        settings.outbound().bindAddress(),
                        settings.outbound().smartHostPort(),
                        settings.outbound().backlog(),
                        settings.outbound().maxConnections()),
                new GmEdgePolicyResponse.PostfixResponse(
                        settings.postfix().host(),
                        settings.postfix().port()),
                new GmEdgePolicyResponse.TlsResponse(
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
                        settings.tls().trustAll()),
                new GmEdgePolicyResponse.LimitsResponse(
                        settings.limits().connectTimeoutMs(),
                        settings.limits().readTimeoutMs(),
                        settings.limits().maxMessageSizeBytes(),
                        settings.limits().maxLineLengthBytes(),
                        settings.limits().maxRecipients()),
                settings.routes().stream()
                        .map(route -> new GmEdgePolicyResponse.RouteResponse(
                                route.domainPattern(),
                                route.targetHost(),
                                route.targetPort(),
                                route.security()))
                        .toList(),
                settings.updatedAt());
    }

    private SmimeSuitePolicyResponse toSmimeSuiteResponse(SmimeSuitePolicyPort.SmimeSuitePolicySettings settings) {
        return new SmimeSuitePolicyResponse(
                settings.defaultStandardSuite(),
                settings.defaultGmSuite(),
                settings.standardSuites().stream()
                        .map(this::toSmimeSuiteOptionResponse)
                        .toList(),
                settings.gmSuites().stream()
                        .map(this::toSmimeSuiteOptionResponse)
                        .toList(),
                settings.updatedAt());
    }

    private SmimeSuitePolicyResponse.SmimeSuiteOptionResponse toSmimeSuiteOptionResponse(
            SmimeSuitePolicyPort.SmimeSuiteOption option) {
        return new SmimeSuitePolicyResponse.SmimeSuiteOptionResponse(
                option.id(),
                option.displayName(),
                option.profile());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
