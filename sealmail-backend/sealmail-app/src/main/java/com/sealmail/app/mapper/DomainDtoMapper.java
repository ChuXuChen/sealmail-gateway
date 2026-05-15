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
            case "GM_ONLY" -> "国密优先";
            case "STANDARD_ONLY" -> "国际优先";
            default -> algorithm;
        };
    }
}
