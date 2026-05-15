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
public class CertificateResponse {

    private String id;
    private String thumbprint;
    private String alias;
    private String ownerEmail;
    private String subjectDn;
    private String issuerDn;
    private String serialNumber;

    private boolean trusted;
    private boolean chainUsable;
    private boolean revoked;
    private String revocationReason;

    private boolean suitableForSigning;
    private boolean suitableForEncryption;
    private boolean hasPrivateKey;

    private String algorithm;
    private String subjectKeyIdentifier;
    private java.util.List<String> keyUsages;

    // ---- CA / chain ----
    private boolean ca;
    private Integer pathLenConstraint;
    private String issuerCertId;
    private java.util.List<String> extendedKeyUsages;
    private String crlDistributionPointUrl;
    private boolean importedCrlAvailable;
    private Instant revocationDate;
    private String revocationCrlReason;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant notBefore;
    private Instant notAfter;
}
