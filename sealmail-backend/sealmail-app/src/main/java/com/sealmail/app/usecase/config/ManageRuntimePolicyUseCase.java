package com.sealmail.app.usecase.config;

import com.sealmail.app.dto.request.QuarantinePolicyRequest;
import com.sealmail.app.dto.request.RelayPolicyRequest;
import com.sealmail.app.dto.response.QuarantinePolicyResponse;
import com.sealmail.app.dto.response.RelayPolicyResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.config.QuarantinePolicyPort;
import com.sealmail.domain.config.RelayPolicyPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageRuntimePolicyUseCase {

    private final RelayPolicyPort relayPolicyPort;
    private final QuarantinePolicyPort quarantinePolicyPort;

    public ManageRuntimePolicyUseCase(RelayPolicyPort relayPolicyPort,
                                      QuarantinePolicyPort quarantinePolicyPort) {
        this.relayPolicyPort = relayPolicyPort;
        this.quarantinePolicyPort = quarantinePolicyPort;
    }

    @Transactional(readOnly = true)
    public RelayPolicyResponse getRelayPolicy(UserContext user) {
        requireAdmin(user, "只有管理员可以查看 Relay 策略");
        return toRelayResponse(relayPolicyPort.getSettings());
    }

    @Transactional
    public RelayPolicyResponse updateRelayPolicy(RelayPolicyRequest request, UserContext user) {
        requireAdmin(user, "只有管理员可以更新 Relay 策略");
        return toRelayResponse(relayPolicyPort.updateSettings(toRelayUpdate(request)));
    }

    @Transactional(readOnly = true)
    public QuarantinePolicyResponse getQuarantinePolicy(UserContext user) {
        requireAdmin(user, "只有管理员可以查看隔离策略");
        return toQuarantineResponse(quarantinePolicyPort.getSettings());
    }

    @Transactional
    public QuarantinePolicyResponse updateQuarantinePolicy(QuarantinePolicyRequest request, UserContext user) {
        requireAdmin(user, "只有管理员可以更新隔离策略");
        return toQuarantineResponse(quarantinePolicyPort.updateSettings(toQuarantineUpdate(request)));
    }

    private RelayPolicyPort.RelayPolicySettingsUpdate toRelayUpdate(RelayPolicyRequest request) {
        if (request == null) {
            return new RelayPolicyPort.RelayPolicySettingsUpdate(null, null, null, null, null, null, null, null, null);
        }
        return new RelayPolicyPort.RelayPolicySettingsUpdate(
                request.enabled(),
                request.host(),
                request.port(),
                request.useTls(),
                request.username(),
                request.passwordSecretRef(),
                request.clearPasswordSecretRef(),
                request.timeoutMs(),
                request.envelopeFrom());
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

    private RelayPolicyResponse toRelayResponse(RelayPolicyPort.RelayPolicySettings settings) {
        return new RelayPolicyResponse(
                settings.enabled(),
                settings.host(),
                settings.port(),
                settings.useTls(),
                settings.username(),
                settings.passwordConfigured(),
                settings.passwordSecretRef(),
                settings.timeoutMs(),
                settings.envelopeFrom(),
                settings.updatedAt());
    }

    private QuarantinePolicyResponse toQuarantineResponse(QuarantinePolicyPort.QuarantinePolicySettings settings) {
        return new QuarantinePolicyResponse(
                settings.maxRetentionDays(),
                settings.notificationEnabled(),
                settings.releaseRequiresEncryption(),
                settings.updatedAt());
    }

    private void requireAdmin(UserContext user, String message) {
        if (user == null || !user.isAdmin()) {
            throw BusinessException.forbidden(message);
        }
    }
}
