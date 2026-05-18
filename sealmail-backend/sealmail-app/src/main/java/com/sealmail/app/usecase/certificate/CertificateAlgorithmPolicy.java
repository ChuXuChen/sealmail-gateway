package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
class CertificateAlgorithmPolicy {

    void requireSameAlgorithm(String issuerRole,
                              String issuerAlgorithm,
                              String subjectRole,
                              String subjectAlgorithm) {
        String issuer = normalize(issuerAlgorithm);
        String subject = normalize(subjectAlgorithm);
        if (issuer == null || subject == null) {
            throw BusinessException.badRequest(issuerRole + " 与 " + subjectRole + " 算法必须明确");
        }
        if (!issuer.equals(subject)) {
            throw BusinessException.badRequest(
                    subjectRole + " 算法必须与 " + issuerRole + " 算法一致: "
                            + subject + " 不能由 " + issuer + " 签发");
        }
    }

    private String normalize(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return null;
        }
        return algorithm.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
