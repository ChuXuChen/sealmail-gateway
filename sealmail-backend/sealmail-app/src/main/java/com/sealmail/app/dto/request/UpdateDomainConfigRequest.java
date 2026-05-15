package com.sealmail.app.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新域名配置请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDomainConfigRequest {

    private String encryptionPolicy;
    private String preferredAlgorithm;
    private Boolean signingEnabled;
    private Boolean dkimEnabled;
    private Boolean active;
}
