package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Self-signed certificate generation: pubkey, privkey and signature all come from
 * a freshly generated keypair owned by {@code ownerEmail}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateCertificateRequest {

    @NotBlank(message = "Owner email is required")
    @Email
    private String ownerEmail;

    @NotBlank(message = "Algorithm is required")
    @Pattern(regexp = "RSA|SM2", message = "Algorithm must be RSA or SM2")
    private String algorithm;

    private String subjectDn;

    private String alias;

    @Builder.Default
    private Integer validityDays = 365;

    @Builder.Default
    private Boolean trusted = Boolean.TRUE;
}
