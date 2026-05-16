package com.sealmail.app.mapper;

import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.usecase.certificate.CertificateChainService;
import com.sealmail.domain.certificate.Certificate;
import org.springframework.stereotype.Component;

@Component
public class CertificateDtoMapper {

    private final CertificateChainService certificateChainService;

    public CertificateDtoMapper(CertificateChainService certificateChainService) {
        this.certificateChainService = certificateChainService;
    }

    public CertificateResponse toResponse(Certificate certificate) {
        String algorithm = certificate.getAlgorithm();
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = "UNKNOWN";
        }
        boolean chainUsable = certificateChainService.isChainTrustedAndUsable(certificate);
        return CertificateResponse.builder()
                .id(certificate.getId().getThumbprint())
                .thumbprint(certificate.getId().getThumbprint())
                .alias(certificate.getAlias())
                .ownerEmail(certificate.getOwner().getValue())
                .subjectDn(certificate.getSubjectDn())
                .issuerDn(certificate.getIssuerDn())
                .serialNumber(certificate.getSerialNumber().toString())
                .subjectKeyIdentifier(certificate.getSubjectKeyIdentifier())
                .trusted(certificate.isTrusted())
                .chainUsable(chainUsable)
                .revoked(certificate.isRevoked())
                .revocationReason(certificate.getRevocationReason())
                .suitableForSigning(chainUsable && certificate.isSuitableForSigning())
                .suitableForEncryption(chainUsable && certificate.isSuitableForEncryption())
                .hasPrivateKey(certificate.hasPrivateKey())
                .algorithm(algorithm)
                .keyUsages(certificate.getKeyUsages().stream().map(Enum::name).toList())
                .ca(certificate.isCA())
                .pathLenConstraint(certificate.getPathLenConstraint())
                .issuerCertId(certificate.getIssuerCertId())
                .extendedKeyUsages(certificate.getExtendedKeyUsages().stream().sorted().toList())
                .crlDistributionPointUrl(certificate.getCrlDistributionPointUrl())
                .importedCrlAvailable(certificate.hasImportedCrl())
                .revocationDate(certificate.getRevocationDate())
                .revocationCrlReason(certificate.getRevocationCrlReason())
                .createdAt(certificate.getCreatedAt())
                .updatedAt(certificate.getUpdatedAt())
                .notBefore(certificate.getValidity().getNotBefore())
                .notAfter(certificate.getValidity().getNotAfter())
                .build();
    }

}
