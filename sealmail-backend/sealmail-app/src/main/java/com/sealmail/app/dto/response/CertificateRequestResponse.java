package com.sealmail.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateRequestResponse {

    private String id;
    private String status;
    private String requestedOwnerEmail;
    private String submittedBy;
    private String submitterIp;
    private Instant submittedAt;
    private Instant decidedAt;
    private String decidedBy;
    private String decisionComment;
    private String issuedCertId;
    private String intermediateCaId;
    /** Trimmed first 80 chars of the CSR PEM, just for the queue list display. */
    private String csrPreview;
}
