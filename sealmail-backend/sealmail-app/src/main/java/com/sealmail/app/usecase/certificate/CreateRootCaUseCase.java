package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.CreateRootCaRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyProvider;
import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateRootCaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateRootCaUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final CertificateMaterialAssembler certificateMaterialAssembler;
    private final KeyManagementPort keyManagementPort;
    private final PermissionChecker permissionChecker;
    private final int defaultRootValidityDays;

    public CreateRootCaUseCase(CertificateRepository certificateRepository,
                               CertificateDtoMapper mapper,
                               CertificateMaterialAssembler certificateMaterialAssembler,
                               KeyManagementPort keyManagementPort,
                               PermissionChecker permissionChecker,
                               @Value("${sealmail.ca.default-root-validity-days:3650}") int defaultRootValidityDays) {
        this.certificateRepository = certificateRepository;
        this.mapper = mapper;
        this.certificateMaterialAssembler = certificateMaterialAssembler;
        this.keyManagementPort = keyManagementPort;
        this.permissionChecker = permissionChecker;
        this.defaultRootValidityDays = defaultRootValidityDays;
    }

    public CertificateResponse execute(CreateRootCaRequest request, UserContext user) {
        permissionChecker.checkCanManageCa(user);
        try {
            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getCommonName() + ", O=SealMail, C=CN";
            }
            int validity = request.getValidityDays() != null ? request.getValidityDays() : defaultRootValidityDays;

            EmailAddress owner = new EmailAddress("ca-" + request.getAlgorithm().toLowerCase() + "@sealmail.local");
            KeyManagementPort.ManagedCertificateMaterial material = keyManagementPort.issueSelfSigned(
                    new KeyProvider.IssueSelfSignedManagedCommand(
                            owner.getValue(),
                            subjectDn,
                            request.getAlgorithm(),
                            validity,
                            true,
                            1,
                            java.util.Set.of(),
                            null,
                            KeyPurpose.CA_SIGNING));

            String thumbprint = material.certificate().thumbprint();
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.conflict("Root CA already exists with thumbprint: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.issued(material.certificate(), owner);
            cert.setPrivateKeySecretRef(material.keyRecord().managedRef());
            cert.markAsCA(1);
            cert.setIssuerCertId(null); // self-signed root
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            cert.trust(); // Roots are trusted on creation by definition
            certificateRepository.save(cert);

            log.info("User [{}] created Root CA {} ({}) cn={}",
                    user.getUserId(), thumbprint, request.getAlgorithm(), request.getCommonName());
            return mapper.toResponse(cert);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Root CA: {}", e.getMessage(), e);
            throw new RuntimeException("Root CA 创建失败: " + e.getMessage(), e);
        }
    }
}
