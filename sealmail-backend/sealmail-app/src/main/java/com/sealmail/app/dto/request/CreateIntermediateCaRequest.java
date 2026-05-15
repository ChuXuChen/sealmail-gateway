package com.sealmail.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create an Intermediate CA signed by an existing Root CA.
 * BasicConstraints CA=true, pathLen=0 (cannot sign further CAs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIntermediateCaRequest {

    /** Thumbprint of the Root CA that will sign this Intermediate. */
    @NotBlank(message = "Root CA id is required")
    private String rootCaId;

    @NotBlank(message = "Common name is required")
    private String commonName;

    @NotBlank(message = "Algorithm is required")
    @Pattern(regexp = "RSA|SM2", message = "Algorithm must be RSA or SM2")
    private String algorithm;

    private String subjectDn;

    private String alias;

    private Integer validityDays;
}
