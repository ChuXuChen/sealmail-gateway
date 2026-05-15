package com.sealmail.app.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportCrlRequest {

    private String crlPem;

    /** Base64 encoded DER .crl content. */
    private String crlDerBase64;
}
