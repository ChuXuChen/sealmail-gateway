package com.sealmail.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sign an externally-generated CSR using a CA certificate that holds its private key.
 * The signed certificate is stored without a private key, since the requestor holds it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignCsrRequest {

    @NotBlank(message = "CA certificate id is required")
    private String caCertId;

    @NotBlank(message = "CSR PEM data is required")
    private String csrPem;

    private String alias;

    @Builder.Default
    private Integer validityDays = 365;

    @Builder.Default
    private Boolean trusted = Boolean.TRUE;
}
