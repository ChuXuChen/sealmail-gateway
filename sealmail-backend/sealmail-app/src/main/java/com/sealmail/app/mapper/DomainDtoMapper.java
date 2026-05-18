package com.sealmail.app.mapper;

import com.sealmail.app.dto.response.DomainConfigResponse;
import com.sealmail.domain.policy.DomainConfig;
import org.springframework.stereotype.Component;

/**
 * 域名配置对象映射器
 */
@Component
public class DomainDtoMapper {

    public DomainConfigResponse toResponse(DomainConfig config) {
        return DomainConfigResponse.builder()
                .id(config.getId())
                .domain(config.getDomain())
                .localDomain(config.isLocalDomain())
                .encryptionPolicy(config.getEncryptionPolicy().name())
                .encryptionPolicyDisplayName(getPolicyDisplayName(config.getEncryptionPolicy().name()))
                .preferredAlgorithm(config.getPreferredAlgorithm().name())
                .preferredAlgorithmDisplayName(getAlgorithmDisplayName(config.getPreferredAlgorithm().name()))
                .signingEnabled(config.isSigningEnabled())
                .dkimEnabled(config.isDkimEnabled())
                .deliveryHost(config.getDeliveryHost())
                .deliveryTransportProfile(config.getDeliveryTransportProfile().name())
                .deliveryTransportProfileDisplayName(getTransportProfileDisplayName(
                        config.getDeliveryTransportProfile().name()))
                .deliveryPort(config.getDeliveryPort())
                .decryptionMode(config.getDecryptionMode().name())
                .decryptionModeDisplayName(getDecryptionModeDisplayName(config.getDecryptionMode().name()))
                .active(config.isActive())
                .build();
    }

    private String getPolicyDisplayName(String policy) {
        return switch (policy) {
            case "MANDATORY" -> "强制加密";
            case "ALLOW" -> "允许加密";
            case "NO_ENCRYPTION" -> "禁用加密";
            default -> policy;
        };
    }

    private String getAlgorithmDisplayName(String algorithm) {
        return switch (algorithm) {
            case "AUTO" -> "自动选择";
            case "GM_ONLY" -> "仅国密";
            case "STANDARD_ONLY" -> "仅国际";
            default -> algorithm;
        };
    }

    private String getTransportProfileDisplayName(String profile) {
        return switch (profile) {
            case "SMTP_CLEAR" -> "SMTP 明文";
            case "SMTP_STARTTLS_STANDARD" -> "SMTP STARTTLS 国际 TLS";
            case "SMTP_IMPLICIT_TLS_STANDARD" -> "SMTP 隐式国际 TLS";
            case "SMTP_STARTTLS_GM" -> "SMTP STARTTLS 国密";
            case "SMTP_IMPLICIT_TLS_GM" -> "SMTP 隐式国密 TLS";
            default -> profile;
        };
    }

    private String getDecryptionModeDisplayName(String mode) {
        return switch (mode) {
            case "GATEWAY_TERMINATED" -> "网关代理解密";
            case "END_TO_END_PASSTHROUGH" -> "端到端透传";
            default -> mode;
        };
    }
}
