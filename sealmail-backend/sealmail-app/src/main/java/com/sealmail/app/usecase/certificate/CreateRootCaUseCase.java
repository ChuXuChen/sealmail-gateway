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
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateRootCaUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateRootCaUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final CertificateCryptoService cryptoService;
    private final PermissionChecker permissionChecker;

    @Value("${sealmail.ca.default-root-validity-days:3650}")
    private int defaultRootValidityDays;

    public CertificateResponse execute(CreateRootCaRequest request, UserContext user) {
        permissionChecker.checkCanManageCa(user);
        try {
            KeyPair keyPair = cryptoService.generateKeyPair(request.getAlgorithm());

            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getCommonName() + ", O=SealMail, C=CN";
            }
            int validity = request.getValidityDays() != null ? request.getValidityDays() : defaultRootValidityDays;

            X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                    .subjectPubKey(keyPair.getPublic())
                    .subjectDn(subjectDn)
                    .subjectAlgorithm(request.getAlgorithm())
                    .issuerDn(subjectDn)
                    .issuerPrivKey(keyPair.getPrivate())
                    .issuerPubKey(keyPair.getPublic())
                    .validityDays(validity)
                    .ca(true)
                    .pathLenConstraint(1) // root signs intermediates
                    .build());

            String thumbprint = cryptoService.computeThumbprint(x509);
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.conflict("Root CA already exists with thumbprint: " + thumbprint);
            }

            EmailAddress owner = new EmailAddress("ca-" + request.getAlgorithm().toLowerCase() + "@sealmail.local");
            Certificate cert = cryptoService.toIssuedDomainCertificate(certId, owner, x509, request.getAlgorithm());
            cert.setPrivateKeyData(cryptoService.privateKeyToPem(keyPair.getPrivate()));
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
