package com.sealmail.app.dto.request;

import com.sealmail.domain.policy.DomainName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建域名配置请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDomainConfigRequest {

    @NotBlank(message = "域名不能为空")
    @Pattern(regexp = "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\\.?$",
            message = "请输入有效的域名")
    private String domain;

    private Boolean localDomain;
    private String encryptionPolicy;
    private String preferredAlgorithm;
    private Boolean signingEnabled;
    private Boolean dkimEnabled;
    private Boolean active;

    public void setDomain(String domain) {
        this.domain = DomainName.normalize(domain);
    }
}
