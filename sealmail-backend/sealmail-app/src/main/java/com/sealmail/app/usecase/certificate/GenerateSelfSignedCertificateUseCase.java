package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.GenerateCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Generate a self-signed end-entity certificate (no chain). Intended for quick
 * test setup; production end-entity certs should be issued under a CA via
 * {@link IssueEndEntityUseCase}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GenerateSelfSignedCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateSelfSignedCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificateMaterialAssembler certificateMaterialAssembler;
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService;

    public CertificateResponse execute(GenerateCertificateRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.getOwnerEmail());
        permissionChecker.checkCanManageCertificates(user, owner.getDomain());

        try {
            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getOwnerEmail() + ", O=SealMail, C=CN";
            }

            int validity = request.getValidityDays() != null ? request.getValidityDays() : 365;

            CertificateCryptoPort.CertificateMaterial material =
                    certificateCryptoPort.issueSelfSigned(new CertificateCryptoPort.IssueSelfSignedCommand(
                            subjectDn,
                            request.getAlgorithm(),
                            validity,
                            false,
                            0,
                            Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                            null));

            String thumbprint = material.certificate().thumbprint();
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw new IllegalArgumentException("Certificate already exists with thumbprint: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.issued(material.certificate(), owner);
            privateKeyMaterialService.store(cert, material.privateKeyPem());
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            if (!Boolean.FALSE.equals(request.getTrusted())) {
                cert.trust();
            }
            certificateRepository.save(cert);

            log.info("User [{}] generated self-signed {} certificate {} for {}",
                    user.getUserId(), request.getAlgorithm(), thumbprint, request.getOwnerEmail());
            return mapper.toResponse(cert);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate self-signed certificate: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate self-signed certificate: " + e.getMessage(), e);
        }
    }
}
