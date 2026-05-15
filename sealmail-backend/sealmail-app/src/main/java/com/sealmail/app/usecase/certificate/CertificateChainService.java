package com.sealmail.app.usecase.certificate;

import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CertificateChainService {

    private final CertificateRepository certificateRepository;

    public CertificateChainService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    public List<Certificate> loadAncestorsIncludingSelf(Certificate certificate) {
        List<Certificate> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Certificate current = certificate;

        while (current != null) {
            String currentId = current.getId().getThumbprint();
            if (!visited.add(currentId)) {
                break;
            }
            chain.add(current);
            String issuerCertId = current.getIssuerCertId();
            if (issuerCertId == null || issuerCertId.isBlank()) {
                break;
            }
            current = certificateRepository.findById(new CertificateId(issuerCertId)).orElse(null);
        }

        return chain;
    }

    public boolean isChainTrustedAndUsable(Certificate certificate) {
        for (Certificate node : loadAncestorsIncludingSelf(certificate)) {
            if (!node.isTrusted() || node.isRevoked() || node.getValidity().isExpired()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasChildren(String certificateId) {
        return !certificateRepository.findByIssuerCertId(certificateId).isEmpty();
    }

    public boolean hasUsableIssuerChain(Certificate certificate) {
        String issuerCertId = certificate.getIssuerCertId();
        if (issuerCertId == null || issuerCertId.isBlank()) {
            return true;
        }
        Certificate issuer = certificateRepository.findById(new CertificateId(issuerCertId)).orElse(null);
        return issuer != null && isChainTrustedAndUsable(issuer);
    }
}
