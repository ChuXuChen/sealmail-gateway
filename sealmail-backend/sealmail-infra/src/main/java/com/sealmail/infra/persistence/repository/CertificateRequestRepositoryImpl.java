package com.sealmail.infra.persistence.repository;

import com.sealmail.domain.certificate.CertificateRequest;
import com.sealmail.domain.certificate.CertificateRequestRepository;
import com.sealmail.infra.persistence.entity.CertificateRequestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CertificateRequestRepositoryImpl implements CertificateRequestRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public CertificateRequest save(CertificateRequest req) {
        CertificateRequestEntity entity = toEntity(req);

        CertificateRequestEntity existing = em.find(CertificateRequestEntity.class, entity.getId());
        if (existing != null) {
            entity.setVersion(existing.getVersion());
            em.merge(entity);
        } else {
            em.persist(entity);
        }
        return req;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificateRequest> findById(String id) {
        CertificateRequestEntity entity = em.find(CertificateRequestEntity.class, id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateRequest> findAll() {
        TypedQuery<CertificateRequestEntity> q = em.createQuery(
                "SELECT r FROM CertificateRequestEntity r ORDER BY r.submittedAt DESC",
                CertificateRequestEntity.class);
        return q.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateRequest> findByStatus(CertificateRequest.Status status) {
        TypedQuery<CertificateRequestEntity> q = em.createQuery(
                "SELECT r FROM CertificateRequestEntity r WHERE r.status = :status ORDER BY r.submittedAt DESC",
                CertificateRequestEntity.class);
        q.setParameter("status", status.name());
        return q.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        CertificateRequestEntity entity = em.find(CertificateRequestEntity.class, id);
        if (entity != null) em.remove(entity);
    }

    private CertificateRequestEntity toEntity(CertificateRequest r) {
        CertificateRequestEntity e = new CertificateRequestEntity();
        e.setId(r.getId());
        e.setCsrPem(r.getCsrPem());
        e.setRequestedOwnerEmail(r.getRequestedOwnerEmail());
        e.setSubmittedBy(r.getSubmittedBy());
        e.setSubmitterIp(r.getSubmitterIp());
        e.setSubmittedAt(r.getSubmittedAt());
        e.setStatus(r.getStatus().name());
        e.setDecidedAt(r.getDecidedAt());
        e.setDecidedBy(r.getDecidedBy());
        e.setDecisionComment(r.getDecisionComment());
        e.setIssuedCertId(r.getIssuedCertId());
        e.setIntermediateCaId(r.getIntermediateCaId());
        return e;
    }

    private CertificateRequest toDomain(CertificateRequestEntity e) {
        CertificateRequest r = CertificateRequest.restore(
                e.getId(), e.getCsrPem(), e.getRequestedOwnerEmail(),
                e.getSubmittedBy(), e.getSubmitterIp(),
                e.getSubmittedAt(),
                CertificateRequest.Status.valueOf(e.getStatus()));
        r.rehydrate(
                CertificateRequest.Status.valueOf(e.getStatus()),
                e.getDecidedAt(),
                e.getDecidedBy(),
                e.getDecisionComment(),
                e.getIssuedCertId(),
                e.getIntermediateCaId());
        r.clearDomainEvents();
        return r;
    }
}
