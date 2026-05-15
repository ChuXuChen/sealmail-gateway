package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ApproveCertRequestRequest;
import com.sealmail.app.dto.request.IssueEndEntityRequest;
import com.sealmail.app.dto.request.SignCsrRequest;
import com.sealmail.app.dto.request.SubmitCertRequestRequest;
import com.sealmail.app.dto.response.CertificateRequestResponse;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.CertificateRequest;
import com.sealmail.domain.certificate.CertificateRequestRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The async CSR lifecycle: anonymous client submits CSR → PENDING; admin
 * approves (picking an Intermediate CA) → APPROVED → signed → ISSUED; or admin
 * rejects → REJECTED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CertificateRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(CertificateRequestUseCase.class);

    private final CertificateRequestRepository repository;
    private final SignCsrUseCase signCsrUseCase;
    private final CertificateCryptoService cryptoService;
    private final PermissionChecker permissionChecker;

    /** Anonymous submission. */
    public CertificateRequestResponse submit(SubmitCertRequestRequest request, String submitterIp) {
        try {
            // Validate CSR is parseable; pull email out for the queue record.
            PKCS10CertificationRequest csr = cryptoService.parseCsr(request.getCsrPem());
            cryptoService.validateCsr(csr);
            String emailFromCsr = extractEmail(csr);
            String requestedOwner = request.getRequestedOwnerEmail() != null
                    ? request.getRequestedOwnerEmail()
                    : emailFromCsr;
            if (emailFromCsr == null || emailFromCsr.isBlank()) {
                throw BusinessException.badRequest("CSR 的 Subject 中未找到 emailAddress 或可识别的邮箱");
            }
            if (request.getRequestedOwnerEmail() != null
                    && !request.getRequestedOwnerEmail().isBlank()
                    && !request.getRequestedOwnerEmail().equalsIgnoreCase(emailFromCsr)) {
                throw BusinessException.badRequest(
                        "申请邮箱与 CSR Subject 中的邮箱不一致: requestedOwnerEmail="
                                + request.getRequestedOwnerEmail() + ", csrEmail=" + emailFromCsr);
            }

            CertificateRequest entity = CertificateRequest.submit(
                    UUID.randomUUID().toString(),
                    request.getCsrPem(),
                    requestedOwner,
                    null,                       // anonymous
                    submitterIp);

            repository.save(entity);
            log.info("CSR request submitted: id={} owner={} ip={}",
                    entity.getId(), requestedOwner, submitterIp);
            return toResponse(entity);
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无法解析 CSR: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to submit CSR request: {}", e.getMessage(), e);
            throw new RuntimeException("CSR 提交失败: " + e.getMessage(), e);
        }
    }

    public CertificateResponse approve(String requestId, ApproveCertRequestRequest body, UserContext user) {
        permissionChecker.checkCanReviewCsr(user);
        CertificateRequest req = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("CertificateRequest", requestId));

        if (req.getStatus() != CertificateRequest.Status.PENDING) {
            throw BusinessException.badRequest("只能审批 PENDING 状态的请求，当前状态：" + req.getStatus());
        }
        assertRequestedOwnerMatchesCsr(req.getRequestedOwnerEmail(), req.getCsrPem());

        req.approve(user.getUserId(), body.getIntermediateCaId(), body.getComment());
        repository.save(req);

        // Now invoke SignCsrUseCase to issue the actual certificate.
        SignCsrRequest signReq = SignCsrRequest.builder()
                .caCertId(body.getIntermediateCaId())
                .csrPem(req.getCsrPem())
                .validityDays(body.getValidityDays() != null ? body.getValidityDays() : 365)
                .trusted(Boolean.TRUE)
                .build();
        CertificateResponse issued = signCsrUseCase.execute(signReq, user);

        req.markIssued(issued.getId());
        repository.save(req);

        log.info("Admin [{}] approved CSR {} -> issued cert {}",
                user.getUserId(), requestId, issued.getId());
        return issued;
    }

    public CertificateRequestResponse reject(String requestId, String comment, UserContext user) {
        permissionChecker.checkCanReviewCsr(user);
        CertificateRequest req = repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("CertificateRequest", requestId));
        req.reject(user.getUserId(), comment);
        repository.save(req);
        log.info("Admin [{}] rejected CSR {}", user.getUserId(), requestId);
        return toResponse(req);
    }

    private void assertRequestedOwnerMatchesCsr(String requestedOwnerEmail, String csrPem) {
        try {
            PKCS10CertificationRequest csr = cryptoService.parseCsr(csrPem);
            cryptoService.validateCsr(csr);
            String emailFromCsr = extractEmail(csr);
            if (emailFromCsr == null || emailFromCsr.isBlank()) {
                throw BusinessException.badRequest("CSR 的 Subject 中未找到 emailAddress 或可识别的邮箱");
            }
            if (requestedOwnerEmail != null
                    && !requestedOwnerEmail.isBlank()
                    && !requestedOwnerEmail.equalsIgnoreCase(emailFromCsr)) {
                throw BusinessException.badRequest(
                        "申请邮箱与 CSR Subject 中的邮箱不一致: requestedOwnerEmail="
                                + requestedOwnerEmail + ", csrEmail=" + emailFromCsr);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无法解析 CSR: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("CSR 校验失败: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<CertificateRequestResponse> listAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CertificateRequestResponse> listByStatus(String status) {
        return repository.findByStatus(CertificateRequest.Status.valueOf(status.toUpperCase()))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CertificateRequestResponse findById(String id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("CertificateRequest", id));
    }

    private CertificateRequestResponse toResponse(CertificateRequest r) {
        String preview = r.getCsrPem() != null && r.getCsrPem().length() > 80
                ? r.getCsrPem().substring(0, 80) + "…"
                : r.getCsrPem();
        return CertificateRequestResponse.builder()
                .id(r.getId())
                .status(r.getStatus().name())
                .requestedOwnerEmail(r.getRequestedOwnerEmail())
                .submittedBy(r.getSubmittedBy())
                .submitterIp(r.getSubmitterIp())
                .submittedAt(r.getSubmittedAt())
                .decidedAt(r.getDecidedAt())
                .decidedBy(r.getDecidedBy())
                .decisionComment(r.getDecisionComment())
                .issuedCertId(r.getIssuedCertId())
                .intermediateCaId(r.getIntermediateCaId())
                .csrPreview(preview)
                .build();
    }

    private String extractEmail(PKCS10CertificationRequest csr) {
        for (RDN rdn : csr.getSubject().getRDNs(BCStyle.EmailAddress)) {
            return IETFUtils.valueToString(rdn.getFirst().getValue());
        }
        for (RDN rdn : csr.getSubject().getRDNs(BCStyle.E)) {
            return IETFUtils.valueToString(rdn.getFirst().getValue());
        }
        for (RDN rdn : csr.getSubject().getRDNs(BCStyle.CN)) {
            String cn = IETFUtils.valueToString(rdn.getFirst().getValue());
            if (cn != null && cn.contains("@")) return cn;
        }
        return null;
    }
}
