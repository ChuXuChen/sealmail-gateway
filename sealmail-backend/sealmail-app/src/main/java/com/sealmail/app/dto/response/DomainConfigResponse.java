package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 域名配置响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainConfigResponse {

    private String id;
    private String domain;
    private boolean localDomain;
    private String encryptionPolicy;
    private String encryptionPolicyDisplayName;
    private String preferredAlgorithm;
    private String preferredAlgorithmDisplayName;
    private boolean signingEnabled;
    private boolean dkimEnabled;
    private String deliveryHost;
    private String deliveryTransportProfile;
    private String deliveryTransportProfileDisplayName;
    private Integer deliveryPort;
    private String decryptionMode;
    private String decryptionModeDisplayName;
    private boolean active;
}
