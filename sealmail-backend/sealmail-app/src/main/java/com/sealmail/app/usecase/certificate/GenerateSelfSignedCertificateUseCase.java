package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.GenerateCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
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
    private final CertificateCryptoService cryptoService;

    public CertificateResponse execute(GenerateCertificateRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.getOwnerEmail());
        permissionChecker.checkCanManageCertificates(user, owner.getDomain());

        try {
            KeyPair keyPair = cryptoService.generateKeyPair(request.getAlgorithm());

            String subjectDn = request.getSubjectDn();
            if (subjectDn == null || subjectDn.isBlank()) {
                subjectDn = "CN=" + request.getOwnerEmail() + ", O=SealMail, C=CN";
            }

            int validity = request.getValidityDays() != null ? request.getValidityDays() : 365;

            X509Certificate x509 = cryptoService.issue(CertificateCryptoService.CertSpec.builder()
                    .subjectPubKey(keyPair.getPublic())
                    .subjectDn(subjectDn)
                    .subjectAlgorithm(request.getAlgorithm())
                    .issuerDn(subjectDn)
                    .issuerPrivKey(keyPair.getPrivate())
                    .issuerPubKey(keyPair.getPublic())
                    .validityDays(validity)
                    .ca(false)
                    .ekus(Set.of(CertificateCryptoService.EKU_EMAIL_PROTECTION))
                    .build());

            String thumbprint = cryptoService.computeThumbprint(x509);
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw new IllegalArgumentException("Certificate already exists with thumbprint: " + thumbprint);
            }

            Certificate cert = cryptoService.toIssuedDomainCertificate(certId, owner, x509, request.getAlgorithm());
            cert.setPrivateKeyData(cryptoService.privateKeyToPem(keyPair.getPrivate()));
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
