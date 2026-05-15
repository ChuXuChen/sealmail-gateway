package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Issue an end-entity (S/MIME user) certificate under a chosen Intermediate CA.
 * Generates a fresh keypair for the subject; the private key is stored on the
 * certificate record so the gateway can sign/encrypt mail for this owner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueEndEntityRequest {

    /** Thumbprint of the Intermediate CA. Must have pathLen=0 and a private key. */
    @NotBlank(message = "Intermediate CA id is required")
    private String intermediateCaId;

    @NotBlank(message = "Owner email is required")
    @Email
    private String ownerEmail;

    @NotBlank(message = "Algorithm is required")
    @Pattern(regexp = "RSA|SM2", message = "Algorithm must be RSA or SM2")
    private String algorithm;

    private String subjectDn;

    private String alias;

    private Integer validityDays;

    @Builder.Default
    private Boolean trusted = Boolean.TRUE;
}
