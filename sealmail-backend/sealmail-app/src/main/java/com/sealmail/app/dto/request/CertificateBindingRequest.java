package com.sealmail.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CertificateBindingRequest(
        @NotBlank @Email String ownerEmail,
        @NotBlank String certificateId,
        @NotBlank String purpose,
        @NotNull Boolean enabled
) {
}
