package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.CreateIntermediateCaRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
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
public class CreateIntermediateCaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateIntermediateCaUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final CertificateMaterialAssembler certificateMaterialAssembler;
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService;
    private final KeyManagementPort keyManagementPort;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;
    private final CertificateAlgorithmPolicy certificateAlgorithmPolicy;
    private final int defaultIntermediateValidityDays;

    public CreateIntermediateCaUseCase(CertificateRepository certificateRepository,
                                       CertificateDtoMapper mapper,
                                       CertificateMaterialAssembler certificateMaterialAssembler,
                                       CertificatePrivateKeyMaterialService privateKeyMaterialService,
                                       KeyManagementPort keyManagementPort,
                                       PermissionChecker permissionChecker,
                                       CertificateChainService certificateChainService,
                                       CertificateAlgorithmPolicy certificateAlgorithmPolicy,
                                       @Value("${sealmail.ca.default-intermediate-validity-days:1825}")
                                       int defaultIntermediateValidityDays) {
        this.certificateRepository = certificateRepository;
        this.mapper = mapper;
        this.certificateMaterialAssembler = certificateMaterialAssembler;
        this.privateKeyMaterialService = privateKeyMaterialService;
        this.keyManagementPort = keyManagementPort;
        this.permissionChecker = permissionChecker;
        this.certificateChainService = certificateChainService;
        this.certificateAlgorithmPolicy = certificateAlgorithmPolicy;
        this.defaultIntermediateValidityDays = defaultIntermediateValidityDays;
    }

    public CertificateResponse execute(CreateIntermediateCaRequest request, UserContext user) {
        permissionChecker.checkCanManageCa(user);

        Certificate rootCa = certificateRepository.findById(new CertificateId(request.getRootCaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", request.getRootCaId()));
        if (!rootCa.isCA()) {
            throw BusinessException.badRequest("所选证书不是 CA");
        }
        Integer rootPath = rootCa.getPathLenConstraint();
        if (rootPath == null || rootPath < 1) {
            throw BusinessException.badRequest("所选 Root CA 的 pathLen<1，不允许签发下一级 CA");
        }
        if (!rootCa.hasPrivateKey()) {
            throw BusinessException.badRequest("Root CA 没有关联私钥，无法签发");
        }
        if (rootCa.isRevoked()) {
            throw BusinessException.badRequest("Root CA 已吊销");
        }
        if (!certificateChainService.isChainTrustedAndUsable(rootCa)) {
            throw BusinessException.badRequest("Root CA 链不可信或已失效，无法签发下级 CA");
        }
        certificateAlgorithmPolicy.requireSameAlgorithm(
                "Root CA",
                rootCa.getAlgorithm(),
                "Intermediate CA",
                request.getAlgorithm());

        try {
            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getCommonName() + ", O=SealMail, C=CN";
            }
            int validity = request.getValidityDays() != null
                    ? request.getValidityDays() : defaultIntermediateValidityDays;

            EmailAddress owner = new EmailAddress("ica-" + request.getAlgorithm().toLowerCase() + "@sealmail.local");
            KeyManagementPort.ManagedCertificateMaterial material = keyManagementPort.issueWithIssuer(
                    new KeyProvider.IssueWithManagedIssuerCommand(
                            owner.getValue(),
                            subjectDn,
                            request.getAlgorithm(),
                            rootCa.getPemContent(),
                            validity,
                            true,
                            0,
                            java.util.Set.of(),
                            null,
                            KeyPurpose.CA_SIGNING),
                    privateKeyMaterialService.requireManagedKey(rootCa, "Root CA 没有关联私钥，无法签发").getKeyId());

            String thumbprint = material.certificate().thumbprint();
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.conflict("证书已存在: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.issued(material.certificate(), owner);
            cert.setPrivateKeySecretRef(material.keyRecord().managedRef());
            cert.markAsCA(0);
            cert.setIssuerCertId(rootCa.getId().getThumbprint());
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            cert.trust();
            certificateRepository.save(cert);

            log.info("User [{}] created Intermediate CA {} signed by Root {}",
                    user.getUserId(), thumbprint, request.getRootCaId());
            return mapper.toResponse(cert);

        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Intermediate CA: {}", e.getMessage(), e);
            throw new RuntimeException("Intermediate CA 创建失败: " + e.getMessage(), e);
        }
    }
}
