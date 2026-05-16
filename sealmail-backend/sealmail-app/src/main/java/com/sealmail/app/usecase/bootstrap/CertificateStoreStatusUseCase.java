package com.sealmail.app.usecase.bootstrap;

import com.sealmail.domain.certificate.CertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateStoreStatusUseCase {

    private final CertificateRepository certificateRepository;

    public CertificateStoreStatusUseCase(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return certificateRepository.findAll().isEmpty();
    }
}
