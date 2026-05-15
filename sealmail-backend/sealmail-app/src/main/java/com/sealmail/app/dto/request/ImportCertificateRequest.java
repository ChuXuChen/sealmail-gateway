package com.sealmail.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportCertificateRequest {

    @NotBlank(message = "Owner email is required")
    private String ownerEmail;

    @NotBlank(message = "PEM data is required")
    private String pemData;

    private String privateKeyData;

    private String alias;

    private Boolean trusted = Boolean.FALSE;
}
