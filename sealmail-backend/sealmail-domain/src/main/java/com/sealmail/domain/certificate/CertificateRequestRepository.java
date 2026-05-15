package com.sealmail.domain.certificate;

import java.util.List;
import java.util.Optional;

public interface CertificateRequestRepository {

    CertificateRequest save(CertificateRequest request);

    Optional<CertificateRequest> findById(String id);

    List<CertificateRequest> findAll();

    List<CertificateRequest> findByStatus(CertificateRequest.Status status);

    void deleteById(String id);
}
