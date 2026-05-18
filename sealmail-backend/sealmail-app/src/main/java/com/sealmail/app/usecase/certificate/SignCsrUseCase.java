package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.SignCsrRequest;
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
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyProvider;
import com.sealmail.domain.shared.model.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SignCsrUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignCsrUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificateMaterialAssembler certificateMaterialAssembler;
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService;
    private final KeyManagementPort keyManagementPort;
    private final CertificateChainService certificateChainService;
    private final CertificateAlgorithmPolicy certificateAlgorithmPolicy;
    private final String crlBaseUrl;

    public SignCsrUseCase(CertificateRepository certificateRepository,
                          CertificateDtoMapper mapper,
                          PermissionChecker permissionChecker,
                          CertificateCryptoPort certificateCryptoPort,
                          CertificateMaterialAssembler certificateMaterialAssembler,
                          CertificatePrivateKeyMaterialService privateKeyMaterialService,
                          KeyManagementPort keyManagementPort,
                          CertificateChainService certificateChainService,
                          CertificateAlgorithmPolicy certificateAlgorithmPolicy,
                          @Value("${sealmail.ca.crl-base-url:http://localhost:8080/api/v1/crl/}")
                          String crlBaseUrl) {
        this.certificateRepository = certificateRepository;
        this.mapper = mapper;
        this.permissionChecker = permissionChecker;
        this.certificateCryptoPort = certificateCryptoPort;
        this.certificateMaterialAssembler = certificateMaterialAssembler;
        this.privateKeyMaterialService = privateKeyMaterialService;
        this.keyManagementPort = keyManagementPort;
        this.certificateChainService = certificateChainService;
        this.certificateAlgorithmPolicy = certificateAlgorithmPolicy;
        this.crlBaseUrl = crlBaseUrl;
    }

    public CertificateResponse execute(SignCsrRequest request, UserContext user) {
        Certificate caCert = certificateRepository.findById(new CertificateId(request.getCaCertId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", request.getCaCertId()));
        if (!caCert.isCA() || caCert.getPathLenConstraint() == null || caCert.getPathLenConstraint() != 0) {
            throw BusinessException.badRequest("CSR 必须由 Intermediate CA (pathLen=0) 签发");
        }
        if (!caCert.hasPrivateKey()) {
            throw BusinessException.badRequest("所选 CA 证书没有关联私钥，无法签发");
        }
        if (caCert.isRevoked()) {
            throw BusinessException.badRequest("所选 CA 证书已吊销，无法签发");
        }
        if (!certificateChainService.isChainTrustedAndUsable(caCert)) {
            throw BusinessException.badRequest("所选 CA 证书链不可信或已失效，无法签发");
        }

        try {
            CertificateCryptoPort.CsrInfo csr = certificateCryptoPort.validateCsr(request.getCsrPem());
            certificateAlgorithmPolicy.requireSameAlgorithm(
                    "Intermediate CA",
                    caCert.getAlgorithm(),
                    "CSR",
                    csr.algorithm());
            EmailAddress owner = csr.ownerEmail() == null ? null : new EmailAddress(csr.ownerEmail());
            if (owner == null) {
                throw BusinessException.badRequest("CSR 的 Subject 中未找到 emailAddress 或可识别的邮箱");
            }
            permissionChecker.checkCanManageCertificates(user, owner.getDomain());

            int validity = request.getValidityDays() != null ? request.getValidityDays() : 365;
            String crlUrl = buildCrlUrl(caCert.getId().getThumbprint());
            CertificateCryptoPort.CertificateDescriptor signed = keyManagementPort.signCsr(
                    new KeyProvider.SignCsrManagedCommand(
                            request.getCsrPem(),
                            caCert.getPemContent(),
                            validity,
                            crlUrl),
                    privateKeyMaterialService.requireManagedKey(caCert, "所选 CA 证书没有关联私钥，无法签发")
                            .getKeyId());

            String thumbprint = signed.thumbprint();
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.badRequest("相同指纹的证书已存在: " + thumbprint);
            }

            Certificate cert = certificateMaterialAssembler.issued(signed, owner);
            cert.setIssuerCertId(caCert.getId().getThumbprint());
            cert.setCrlDistributionPointUrl(crlUrl);
            // CSR signing: requestor holds the private key, we don't store it here.
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            if (!Boolean.FALSE.equals(request.getTrusted())) {
                cert.trust();
            }
            certificateRepository.save(cert);

            log.info("User [{}] signed CSR -> certificate {} for {} (CA={})",
                    user.getUserId(), thumbprint, owner.getValue(), request.getCaCertId());

            return mapper.toResponse(cert);

        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sign CSR: {}", e.getMessage(), e);
            throw new RuntimeException("CSR 签发失败: " + e.getMessage(), e);
        }
    }

    private String buildCrlUrl(String caId) {
        String prefix = crlBaseUrl;
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        return prefix + caId;
    }
}
