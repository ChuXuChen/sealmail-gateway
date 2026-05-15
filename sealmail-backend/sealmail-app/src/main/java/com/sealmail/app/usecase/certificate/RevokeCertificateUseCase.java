package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.CertificateException;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevokeCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(RevokeCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;

    @Transactional
    public CertificateResponse execute(String id, String reason, UserContext user) {
        CertificateId certId = new CertificateId(id);
        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
        permissionChecker.checkCanManageCertificates(user, cert.getOwner().getDomain());

        if (cert.isRevoked()) {
            throw CertificateException.revoked(id);
        }

        String finalReason = reason != null && !reason.isBlank() ? reason : "Manually revoked";
        try {
            cert.revoke(finalReason);
        } catch (Exception e) {
            throw CertificateException.invalidCertificate(
                    "Failed to revoke certificate: " + e.getMessage());
        }
        certificateRepository.save(cert);

        // PKIX semantics: revoking a CA invalidates everything it issued. Walk the
        // issuer-cert-id chain and revoke every still-valid descendant. End-entity
        // certs have no descendants so this is a no-op for them.
        int cascaded = cascadeRevokeDescendants(id);

        log.info("User [{}] revoked certificate {} (reason: {}, cascaded {} descendants)",
                user.getUserId(), id, finalReason, cascaded);

        return mapper.toResponse(cert);
    }

    private int cascadeRevokeDescendants(String rootId) {
        int count = 0;
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(rootId);
        visited.add(rootId);

        while (!queue.isEmpty()) {
            String parent = queue.poll();
            List<Certificate> children = certificateRepository.findByIssuerCertId(parent);
            for (Certificate child : children) {
                String childId = child.getId().getThumbprint();
                if (!visited.add(childId)) {
                    continue;
                }
                if (!child.isRevoked()) {
                    String reason = "级联吊销：上级证书 " + parent.substring(0, 16) + "… 已被吊销";
                    try {
                        child.revoke(reason);
                        certificateRepository.save(child);
                        count++;
                    } catch (Exception e) {
                        log.warn("Cascade revoke skipped {} ({}): {}", childId, child.getAlias(), e.getMessage());
                    }
                }
                // Continue regardless of revoked state — already-revoked CAs may still
                // have unrevoked grandchildren that should be reached.
                queue.add(childId);
            }
        }
        return count;
    }
}
