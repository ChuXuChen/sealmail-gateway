package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.spi.CertificatePrivateKeyStore;
import org.springframework.stereotype.Component;

@Component
class CertificatePrivateKeyMaterialService {

    private final CertificatePrivateKeyStore privateKeyStore;

    CertificatePrivateKeyMaterialService(CertificatePrivateKeyStore privateKeyStore) {
        this.privateKeyStore = privateKeyStore;
    }

    void store(Certificate certificate, String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            return;
        }
        String ref = privateKeyStore.store(
                certificate.getOwner().getValue(),
                certificate.getId().getThumbprint(),
                certificate.getPemContent(),
                privateKeyPem);
        certificate.setPrivateKeySecretRef(ref);
    }

    String resolve(Certificate certificate, String missingMessage) {
        if (certificate == null || !certificate.hasPrivateKey()) {
            throw BusinessException.badRequest(missingMessage);
        }
        return privateKeyStore.resolve(certificate.getPrivateKeySecretRef())
                .orElseThrow(() -> BusinessException.badRequest(missingMessage));
    }
}
