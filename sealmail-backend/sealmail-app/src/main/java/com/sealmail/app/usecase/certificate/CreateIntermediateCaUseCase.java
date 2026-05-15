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
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateIntermediateCaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateIntermediateCaUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final CertificateCryptoService cryptoService;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    @Value("${sealmail.ca.default-intermediate-validity-days:1825}")
    private int defaultIntermediateValidityDays;

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

        try {
            KeyPair subjectKeyPair = cryptoService.generateKeyPair(request.getAlgorithm());
            PrivateKey rootPrivKey = cryptoService.parsePrivateKey(rootCa.getPrivateKeyData());
            X509Certificate rootX509 = cryptoService.parseCertificate(rootCa.getPemContent());

            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getCommonName() + ", O=SealMail, C=CN";
            }
            int validity = request.getValidityDays() != null
                    ? request.getValidityDays() : defaultIntermediateValidityDays;

            X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                    .subjectPubKey(subjectKeyPair.getPublic())
                    .subjectDn(subjectDn)
                    .subjectAlgorithm(request.getAlgorithm())
                    .issuerDn(rootX509.getSubjectX500Principal().getName())
                    .issuerPrivKey(rootPrivKey)
                    .issuerPubKey(rootX509.getPublicKey())
                    .validityDays(validity)
                    .ca(true)
                    .pathLenConstraint(0) // cannot sign further CAs
                    .build());

            String thumbprint = cryptoService.computeThumbprint(x509);
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.conflict("证书已存在: " + thumbprint);
            }

            EmailAddress owner = new EmailAddress("ica-" + request.getAlgorithm().toLowerCase() + "@sealmail.local");
            Certificate cert = cryptoService.toIssuedDomainCertificate(certId, owner, x509, request.getAlgorithm());
            cert.setPrivateKeyData(cryptoService.privateKeyToPem(subjectKeyPair.getPrivate()));
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
