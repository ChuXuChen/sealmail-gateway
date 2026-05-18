package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.CertificateResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueryCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(QueryCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateChainService certificateChainService;

    public PageResponse<CertificateResponse> findAll(PageRequest pageRequest, UserContext user) {
        permissionChecker.checkCanViewAllCertificates(user);

        List<Certificate> certs = certificateRepository.findAll();
        log.debug("Found {} certificates (all)", certs.size());

        return paginate(certs, pageRequest);
    }

    public List<CertificateResponse> findAllCAs(UserContext user) {
        permissionChecker.checkCanViewAllCertificates(user);
        return certificateRepository.findAllCAs().stream().map(mapper::toResponse).toList();
    }

    public PageResponse<CertificateResponse> findAllEndEntities(PageRequest pageRequest, UserContext user) {
        permissionChecker.checkAuthenticated(user);
        List<Certificate> certs = certificateRepository.findAllEndEntities().stream()
                .filter(cert -> user.canViewDomain(cert.getOwner().getDomain()))
                .toList();
        return paginate(certs, pageRequest);
    }

    public PageResponse<CertificateResponse> findByOwner(
            String ownerEmail,
            PageRequest pageRequest,
            UserContext user) {

        EmailAddress owner = new EmailAddress(ownerEmail);
        permissionChecker.checkCanViewCertificates(user, owner.getDomain());
        List<Certificate> certs = certificateRepository.findByOwner(owner);

        log.debug("Found {} certificates for owner: {}", certs.size(), ownerEmail);

        return paginate(certs, pageRequest);
    }

    private PageResponse<CertificateResponse> paginate(List<Certificate> certs, PageRequest pageRequest) {
        int total = certs.size();
        int from = Math.min((pageRequest.getPage() - 1) * pageRequest.getSize(), total);
        int to = Math.min(from + pageRequest.getSize(), total);
        List<CertificateResponse> pageItems = certs.subList(from, to).stream()
                .map(mapper::toResponse)
                .toList();
        return PageResponse.of(pageItems, total, pageRequest);
    }

    public CertificateResponse findById(String id, UserContext user) {
        permissionChecker.checkAuthenticated(user);

        Certificate cert = findCertificateForUser(id, user);
        return mapper.toResponse(cert);
    }

    public String exportCertificatePem(String id, UserContext user) {
        Certificate cert = findCertificateForUser(id, user);
        if (cert.isCA()) {
            throw new ResourceNotFoundException("Certificate", id);
        }
        return cert.getPemContent();
    }

    public String exportCaPem(String id, UserContext user) {
        Certificate cert = findCertificateForUser(id, user);
        if (!cert.isCA()) {
            throw new ResourceNotFoundException("CA", id);
        }
        return cert.getPemContent();
    }

    private Certificate findCertificateForUser(String id, UserContext user) {
        permissionChecker.checkAuthenticated(user);
        CertificateId certId = new CertificateId(id);
        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
        permissionChecker.checkCanViewCertificates(user, cert.getOwner().getDomain());
        return cert;
    }

    public boolean isChainUsable(Certificate certificate) {
        return certificateChainService.isChainTrustedAndUsable(certificate);
    }
}
