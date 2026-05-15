package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.exception.DomainException;
import com.sealmail.domain.shared.model.AggregateRoot;

import java.time.Instant;

/**
 * Aggregate for the async CSR request lifecycle: submit (anonymous or authenticated) →
 * pending → approved/rejected → (if approved) issued.
 *
 * The CSR PEM is what an external client generated; once approved the
 * certificate use case signs it and parks the resulting cert thumbprint here.
 */
public class CertificateRequest extends AggregateRoot<String> {

    public enum Status { PENDING, APPROVED, REJECTED, ISSUED }

    private final String csrPem;
    private final String requestedOwnerEmail;
    private final String submittedBy;       // null = anonymous
    private final String submitterIp;       // observability
    private final Instant submittedAt;
    private Status status;
    private Instant decidedAt;
    private String decidedBy;
    private String decisionComment;
    private String issuedCertId;            // populated once status=ISSUED
    private String intermediateCaId;        // CA the approver chose to sign with

    private CertificateRequest(String id, String csrPem, String requestedOwnerEmail,
                                String submittedBy, String submitterIp) {
        super(id);
        this.csrPem = csrPem;
        this.requestedOwnerEmail = requestedOwnerEmail;
        this.submittedBy = submittedBy;
        this.submitterIp = submitterIp;
        this.submittedAt = Instant.now();
        this.status = Status.PENDING;
    }

    private CertificateRequest(String id, String csrPem, String requestedOwnerEmail,
                               String submittedBy, String submitterIp, Instant submittedAt, Status status) {
        super(id);
        this.csrPem = csrPem;
        this.requestedOwnerEmail = requestedOwnerEmail;
        this.submittedBy = submittedBy;
        this.submitterIp = submitterIp;
        this.submittedAt = submittedAt != null ? submittedAt : Instant.now();
        this.status = status != null ? status : Status.PENDING;
    }

    public static CertificateRequest submit(String id, String csrPem,
                                             String requestedOwnerEmail,
                                             String submittedBy, String submitterIp) {
        if (csrPem == null || csrPem.isBlank()) {
            throw new IllegalArgumentException("CSR PEM cannot be blank");
        }
        return new CertificateRequest(id, csrPem, requestedOwnerEmail, submittedBy, submitterIp);
    }

    public static CertificateRequest restore(String id, String csrPem,
                                             String requestedOwnerEmail,
                                             String submittedBy, String submitterIp,
                                             Instant submittedAt, Status status) {
        if (csrPem == null || csrPem.isBlank()) {
            throw new IllegalArgumentException("CSR PEM cannot be blank");
        }
        return new CertificateRequest(id, csrPem, requestedOwnerEmail, submittedBy, submitterIp, submittedAt, status);
    }

    public void approve(String approverUserId, String caId, String comment) {
        if (status != Status.PENDING) {
            throw new DomainException("Only PENDING requests can be approved (was " + status + ")");
        }
        this.status = Status.APPROVED;
        this.decidedAt = Instant.now();
        this.decidedBy = approverUserId;
        this.decisionComment = comment;
        this.intermediateCaId = caId;
    }

    public void reject(String approverUserId, String comment) {
        if (status != Status.PENDING) {
            throw new DomainException("Only PENDING requests can be rejected (was " + status + ")");
        }
        this.status = Status.REJECTED;
        this.decidedAt = Instant.now();
        this.decidedBy = approverUserId;
        this.decisionComment = comment;
    }

    public void markIssued(String certThumbprint) {
        if (status != Status.APPROVED) {
            throw new DomainException("Only APPROVED requests can be marked ISSUED (was " + status + ")");
        }
        this.status = Status.ISSUED;
        this.issuedCertId = certThumbprint;
    }

    public String getCsrPem() { return csrPem; }
    public String getRequestedOwnerEmail() { return requestedOwnerEmail; }
    public String getSubmittedBy() { return submittedBy; }
    public String getSubmitterIp() { return submitterIp; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Status getStatus() { return status; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
    public String getIssuedCertId() { return issuedCertId; }
    public String getIntermediateCaId() { return intermediateCaId; }

    // For rehydration from persistence
    public void rehydrate(Status status, Instant decidedAt, String decidedBy,
                          String decisionComment, String issuedCertId, String intermediateCaId) {
        this.status = status;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.decisionComment = decisionComment;
        this.issuedCertId = issuedCertId;
        this.intermediateCaId = intermediateCaId;
    }
}
