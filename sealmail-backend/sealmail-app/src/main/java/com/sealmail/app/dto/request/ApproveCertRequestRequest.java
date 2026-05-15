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
public class ApproveCertRequestRequest {

    /** Intermediate CA used to sign. Must be pathLen=0. */
    @NotBlank
    private String intermediateCaId;

    private Integer validityDays;

    private String comment;
}
