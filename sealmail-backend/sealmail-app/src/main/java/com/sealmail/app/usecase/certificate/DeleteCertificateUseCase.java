package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeleteCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    @Transactional
    public void execute(String id, UserContext user) {
        CertificateId certId = new CertificateId(id);
        var cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
        if (cert.isCA()) {
            permissionChecker.checkCanManageCa(user);
        } else {
            permissionChecker.checkCanManageCertificates(user, cert.getOwner().getDomain());
        }

        if (certificateChainService.hasChildren(id)) {
            throw BusinessException.conflict("该证书仍有下级证书，不能直接删除；请先处理下级链路");
        }

        certificateRepository.deleteById(certId);

        log.info("User [{}] deleted certificate: {} owner: {}",
                user.getUserId(), id, cert.getOwner().getValue());
    }
}
