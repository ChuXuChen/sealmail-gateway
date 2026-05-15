package com.sealmail.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create a self-signed Root CA: BasicConstraints CA=true, pathLen=1.
 * keyUsage = keyCertSign | cRLSign.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRootCaRequest {

    /** CN of the root. Free-form (e.g. "SealMail Root CA RSA"). */
    @NotBlank(message = "Common name is required")
    private String commonName;

    @NotBlank(message = "Algorithm is required")
    @Pattern(regexp = "RSA|SM2", message = "Algorithm must be RSA or SM2")
    private String algorithm;

    /** Optional override of the full Subject DN; defaults to CN=commonName, O=SealMail, C=CN. */
    private String subjectDn;

    private String alias;

    private Integer validityDays;
}
