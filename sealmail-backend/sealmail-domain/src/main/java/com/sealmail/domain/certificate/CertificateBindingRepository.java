package com.sealmail.domain.certificate;

import com.sealmail.domain.shared.model.EmailAddress;

import java.util.List;
import java.util.Optional;

public interface CertificateBindingRepository {

    CertificateBinding save(CertificateBinding binding);

    Optional<CertificateBinding> findById(String id);

    Optional<CertificateBinding> findByOwnerAndPurpose(EmailAddress owner, CertificateBindingPurpose purpose);

    Optional<CertificateBinding> findActiveByOwnerAndPurpose(EmailAddress owner, CertificateBindingPurpose purpose);

    List<CertificateBinding> findAll();

    List<CertificateBinding> findByDomain(String domain);

    List<CertificateBinding> findByOwner(EmailAddress owner);

    void deleteById(String id);

    void deleteByCertificateId(CertificateId certificateId);
}
