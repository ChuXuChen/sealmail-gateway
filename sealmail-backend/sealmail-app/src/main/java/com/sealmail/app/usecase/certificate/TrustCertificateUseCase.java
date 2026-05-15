package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trust state is a binary admin-controlled flag. Both directions are idempotent
 * (calling trust on an already-trusted cert is a no-op, same for untrust); this
 * keeps the API friendly for a toggle UI that doesn't want to special-case.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrustCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrustCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    @Transactional
    public CertificateResponse execute(String id, UserContext user) {
        CertificateId certId = new CertificateId(id);
        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
        permissionChecker.checkCanManageCertificates(user, cert.getOwner().getDomain());

        if (!cert.isCA() && !certificateChainService.hasUsableIssuerChain(cert)) {
            throw BusinessException.badRequest("上级 CA 链不可信或已失效，不能单独信任该终端证书");
        }

        if (!cert.isTrusted()) {
            cert.trust();
            certificateRepository.save(cert);
            log.info("User [{}] trusted certificate: {}", user.getUserId(), id);
        }
        return mapper.toResponse(cert);
    }

    @Transactional
    public CertificateResponse untrust(String id, UserContext user) {
        CertificateId certId = new CertificateId(id);
        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
        permissionChecker.checkCanManageCertificates(user, cert.getOwner().getDomain());

        if (cert.isTrusted()) {
            cert.untrust();
            certificateRepository.save(cert);
            log.info("User [{}] untrusted certificate: {}", user.getUserId(), id);
        }
        return mapper.toResponse(cert);
    }
}
