package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Issue an S/MIME end-entity certificate under an Intermediate CA. Generates a
 * fresh keypair for the subject; the resulting cert embeds:
 *   - BasicConstraints CA=false
 *   - keyUsage = digitalSignature + (keyEncipherment for RSA / keyAgreement for SM2)
 *   - EKU = emailProtection
 *   - AKI pointing at the Intermediate CA's public key
 *   - CRL DP pointing at the Intermediate's CRL URL
 */
@Service
@Transactional
public class IssueEndEntityUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueEndEntityUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificateMaterialAssembler certificateMaterialAssembler;
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService;
    private final CertificateChainService certificateChainService;
    private final CertificateAlgorithmPolicy certificateAlgorithmPolicy;
    private final String crlBaseUrl;
    private final int defaultEndEntityValidityDays;

    public IssueEndEntityUseCase(CertificateRepository certificateRepository,
                                 CertificateDtoMapper mapper,
                                 PermissionChecker permissionChecker,
                                 CertificateCryptoPort certificateCryptoPort,
                                 CertificateMaterialAssembler certificateMaterialAssembler,
                                 CertificatePrivateKeyMaterialService privateKeyMaterialService,
                                 CertificateChainService certificateChainService,
                                 CertificateAlgorithmPolicy certificateAlgorithmPolicy,
                                 @Value("${sealmail.ca.crl-base-url:http://localhost:8080/api/v1/crl/}")
                                 String crlBaseUrl,
                                 @Value("${sealmail.ca.default-end-entity-validity-days:365}")
                                 int defaultEndEntityValidityDays) {
        this.certificateRepository = certificateRepository;
        this.mapper = mapper;
        this.permissionChecker = permissionChecker;
        this.certificateCryptoPort = certificateCryptoPort;
        this.certificateMaterialAssembler = certificateMaterialAssembler;
        this.privateKeyMaterialService = privateKeyMaterialService;
        this.certificateChainService = certificateChainService;
        this.certificateAlgorithmPolicy = certificateAlgorithmPolicy;
        this.crlBaseUrl = crlBaseUrl;
        this.defaultEndEntityValidityDays = defaultEndEntityValidityDays;
    }

    public CertificateResponse execute(IssueEndEntityRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.getOwnerEmail());
        permissionChecker.checkCanManageCertificates(user, owner.getDomain());

        Certificate intermediateCa = certificateRepository.findById(new CertificateId(request.getIntermediateCaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", request.getIntermediateCaId()));
        validateIntermediate(intermediateCa);
        certificateAlgorithmPolicy.requireSameAlgorithm(
                "Intermediate CA",
                intermediateCa.getAlgorithm(),
                "终端证书",
                request.getAlgorithm());

        try {
            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getOwnerEmail() + ", O=SealMail, C=CN";
            }
            int validity = request.getValidityDays() != null
                    ? request.getValidityDays() : defaultEndEntityValidityDays;
            String crlUrl = buildCrlUrl(intermediateCa.getId().getThumbprint());

            CertificateCryptoPort.CertificateMaterial material =
                    certificateCryptoPort.issueWithIssuer(new CertificateCryptoPort.IssueWithIssuerCommand(
                            subjectDn,
                            request.getAlgorithm(),
                            intermediateCa.getPemContent(),
                            privateKeyMaterialService.resolve(intermediateCa, "Intermediate CA 没有关联私钥，无法签发"),
                            validity,
                            false,
                            0,
                            Set.of(CertificateCryptoPort.EKU_EMAIL_PROTECTION),
                            crlUrl));

            String thumbprint = material.certificate().thumbprint();
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.conflict("证书已存在: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.issued(material.certificate(), owner);
            privateKeyMaterialService.store(cert, material.privateKeyPem());
            cert.setIssuerCertId(intermediateCa.getId().getThumbprint());
            cert.setCrlDistributionPointUrl(crlUrl);
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            if (!Boolean.FALSE.equals(request.getTrusted())) {
                cert.trust();
            }
            certificateRepository.save(cert);

            log.info("User [{}] issued end-entity cert {} for {} under CA {}",
                    user.getUserId(), thumbprint, request.getOwnerEmail(), request.getIntermediateCaId());
            return mapper.toResponse(cert);

        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to issue end-entity certificate: {}", e.getMessage(), e);
            throw new RuntimeException("证书签发失败: " + e.getMessage(), e);
        }
    }

    private void validateIntermediate(Certificate ca) {
        if (!ca.isCA()) {
            throw BusinessException.badRequest("所选证书不是 CA");
        }
        Integer pathLen = ca.getPathLenConstraint();
        if (pathLen == null || pathLen != 0) {
            throw BusinessException.badRequest("签发终端证书必须使用 Intermediate CA (pathLen=0)");
        }
        if (!ca.hasPrivateKey()) {
            throw BusinessException.badRequest("Intermediate CA 没有关联私钥，无法签发");
        }
        if (ca.isRevoked()) {
            throw BusinessException.badRequest("Intermediate CA 已吊销");
        }
        if (!certificateChainService.isChainTrustedAndUsable(ca)) {
            throw BusinessException.badRequest("Intermediate CA 链不可信或已失效，无法签发终端证书");
        }
    }

    private String buildCrlUrl(String caId) {
        String prefix = crlBaseUrl;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + caId;
    }
}
