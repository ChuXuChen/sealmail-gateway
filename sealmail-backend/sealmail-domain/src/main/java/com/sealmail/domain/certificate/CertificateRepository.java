package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository {

    Certificate save(Certificate certificate);

    Optional<Certificate> findById(CertificateId id);

    List<Certificate> findAll();

    List<Certificate> findByOwner(EmailAddress owner);

    /** Certificates whose issuerCertId equals this thumbprint (children in the chain). */
    List<Certificate> findByIssuerCertId(String issuerCertId);

    /** All CA certificates (Root or Intermediate). */
    List<Certificate> findAllCAs();

    /** End-entity (non-CA) certificates. */
    List<Certificate> findAllEndEntities();

    List<Certificate> findTrustedForEncryption(EmailAddress owner);

    List<Certificate> findTrustedForSigning(EmailAddress owner);

    void deleteById(CertificateId id);
}
