package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.key.KeyRecord;
import org.springframework.stereotype.Component;

@Component
class CertificatePrivateKeyMaterialService {

    private final KeyManagementPort keyManagementPort;

    CertificatePrivateKeyMaterialService(KeyManagementPort keyManagementPort) {
        this.keyManagementPort = keyManagementPort;
    }

    KeyRecord store(Certificate certificate, String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            return null;
        }
        KeyRecord keyRecord = keyManagementPort.importCertificateKey(
                certificate.getOwner(),
                certificate.getAlgorithm(),
                certificate.isCA() ? KeyPurpose.CA_SIGNING : KeyPurpose.SMIME,
                certificate.getId().getThumbprint(),
                certificate.getPemContent(),
                privateKeyPem);
        certificate.setPrivateKeySecretRef(keyRecord.managedRef());
        return keyRecord;
    }

    KeyRecord requireManagedKey(Certificate certificate, String missingMessage) {
        if (certificate == null || !certificate.hasPrivateKey()) {
            throw BusinessException.badRequest(missingMessage);
        }
        return keyManagementPort.findActiveKeyForCertificate(certificate.getId().getThumbprint())
                .orElseThrow(() -> BusinessException.badRequest(missingMessage));
    }
}
