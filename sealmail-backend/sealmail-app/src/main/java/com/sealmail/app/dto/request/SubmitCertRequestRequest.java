package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Anonymous CSR submission. The owner email is parsed from the CSR Subject;
 * this field is for cross-check only (helpful in the admin queue UI).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCertRequestRequest {

    @NotBlank
    private String csrPem;

    /** Optional hint — admin sees this in the queue. */
    @Email
    private String requestedOwnerEmail;
}
