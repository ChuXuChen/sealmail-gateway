package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.CertificateException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.certificate.spi.CertificateValidator;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateValidator certificateValidator;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificateMaterialAssembler certificateMaterialAssembler;

    @Transactional
    public CertificateResponse execute(ImportCertificateRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.getOwnerEmail());

        CertificateValidator.ValidationResult validation =
                certificateValidator.validate(request.getPemData());
        if (!validation.isValid()) {
            throw CertificateException.invalidCertificate(
                    "Certificate validation failed: " + validation.reason());
        }

        try {
            CertificateCryptoPort.CertificateDescriptor descriptor =
                    certificateCryptoPort.readCertificate(request.getPemData());
            if (descriptor.ca()) {
                permissionChecker.checkCanManageCa(user);
            } else {
                permissionChecker.checkCanManageCertificates(user, owner.getDomain());
            }

            String thumbprint = descriptor.thumbprint();
            CertificateId certId = new CertificateId(thumbprint);

            if (certificateRepository.findById(certId).isPresent()) {
                throw CertificateException.invalidCertificate(
                        "Certificate already exists with thumbprint: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.imported(descriptor, owner);
            String issuerCertId = resolveIssuerCertId(request.getPemData(), thumbprint);
            if (issuerCertId != null) {
                cert.setIssuerCertId(issuerCertId);
            }

            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            // Import defaults to NOT trusted (admin must explicitly trust unknown certs).
            if (Boolean.TRUE.equals(request.getTrusted())) {
                cert.trust();
            }
            if (request.getPrivateKeyData() != null && !request.getPrivateKeyData().isBlank()) {
                certificateCryptoPort.validateCertificateMatchesPrivateKey(request.getPemData(), request.getPrivateKeyData());
                cert.setPrivateKeyData(request.getPrivateKeyData());
            }

            certificateRepository.save(cert);
            log.info("User [{}] imported {} certificate {} owner={}",
                    user.getUserId(), descriptor.algorithm(), thumbprint, request.getOwnerEmail());

            return mapper.toResponse(cert);

        } catch (IllegalArgumentException e) {
            throw CertificateException.invalidCertificate(e.getMessage());
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to import certificate: {}", e.getMessage(), e);
            throw CertificateException.importFailed(e.getMessage(), e);
        }
    }

    private String resolveIssuerCertId(String importedCertPem, String importedThumbprint) {
        if (certificateCryptoPort.isSelfSigned(importedCertPem)) {
            return null;
        }

        return certificateRepository.findAllCAs().stream()
                .filter(candidate -> !candidate.getId().getThumbprint().equals(importedThumbprint))
                .filter(candidate -> {
                    try {
                        return certificateCryptoPort.isIssuedBy(importedCertPem, candidate.getPemContent());
                    } catch (Exception e) {
                        log.debug("Skipping issuer candidate {}: {}",
                                candidate.getId().getThumbprint(), e.getMessage());
                        return false;
                    }
                })
                .findFirst()
                .map(candidate -> candidate.getId().getThumbprint())
                .orElse(null);
    }
}
