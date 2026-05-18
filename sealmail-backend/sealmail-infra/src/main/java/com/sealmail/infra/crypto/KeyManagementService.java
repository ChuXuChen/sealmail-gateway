package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyOperationResult;
import com.sealmail.domain.key.KeyProvider;
import com.sealmail.domain.key.KeyPurpose;
import com.sealmail.domain.key.KeyRecord;
import com.sealmail.domain.key.KeyRecordRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class KeyManagementService implements KeyManagementPort {

    private final KeyRecordRepository keyRecordRepository;
    private final Map<String, KeyProvider> providers;

    public KeyManagementService(KeyRecordRepository keyRecordRepository, List<KeyProvider> providers) {
        this.keyRecordRepository = keyRecordRepository;
        this.providers = providers.stream().collect(Collectors.toMap(KeyProvider::name, provider -> provider));
    }

    @Override
    public KeyRecord importCertificateKey(EmailAddress owner,
                                          String algorithm,
                                          KeyPurpose purpose,
                                          String certificateId,
                                          String certificatePem,
                                          String privateKeyPem) {
        KeyProvider provider = defaultProvider();
        String providerRef = provider.storePrivateKey(owner.getValue(), certificateId, certificatePem, privateKeyPem);
        KeyRecord keyRecord = KeyRecord.active(
                newKeyId(),
                owner,
                algorithm,
                purpose,
                provider.name(),
                providerRef,
                certificateId);
        return keyRecordRepository.save(keyRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KeyRecord> findActiveKeyForCertificate(String certificateId) {
        return keyRecordRepository.findActiveByCertificateId(certificateId)
                .filter(KeyRecord::active);
    }

    @Override
    @Transactional(readOnly = true)
    public KeyRecord requireActiveKeyForCertificate(String certificateId, String missingMessage) {
        return findActiveKeyForCertificate(certificateId)
                .orElseThrow(() -> new IllegalStateException(missingMessage));
    }

    @Override
    public KeyOperationResult signSmimeForCertificate(String certificateId, byte[] mimeMessage, String certificatePem) {
        KeyRecord keyRecord = requireActiveKeyForCertificate(certificateId, "Managed signing key not found");
        KeyOperationResult result = provider(keyRecord).signSmime(keyRecord, mimeMessage, certificatePem);
        markUsed(keyRecord);
        return result;
    }

    @Override
    public KeyOperationResult decryptSmimeForCertificate(String certificateId, byte[] encryptedMessage, String certificatePem) {
        KeyRecord keyRecord = requireActiveKeyForCertificate(certificateId, "Managed decryption key not found");
        KeyOperationResult result = provider(keyRecord).decryptSmime(keyRecord, encryptedMessage, certificatePem);
        markUsed(keyRecord);
        return result;
    }

    @Override
    public ManagedCertificateMaterial issueSelfSigned(KeyProvider.IssueSelfSignedManagedCommand command) {
        KeyProvider provider = defaultProvider();
        KeyProvider.ManagedCertificateMaterial material = provider.issueSelfSigned(command);
        KeyRecord keyRecord = KeyRecord.active(
                newKeyId(),
                new EmailAddress(command.ownerEmail()),
                material.certificate().algorithm(),
                command.purpose(),
                provider.name(),
                material.privateKeyProviderRef(),
                material.certificate().thumbprint());
        keyRecordRepository.save(keyRecord);
        return new ManagedCertificateMaterial(material.certificate(), keyRecord, material.publicKeyFormat());
    }

    @Override
    public ManagedCertificateMaterial issueWithIssuer(KeyProvider.IssueWithManagedIssuerCommand command, String issuerKeyId) {
        KeyRecord issuerKey = requireActiveKey(issuerKeyId);
        KeyProvider keyProvider = provider(issuerKey);
        KeyProvider.ManagedCertificateMaterial material = keyProvider.issueWithIssuer(command, issuerKey);
        markUsed(issuerKey);
        KeyRecord subjectKey = KeyRecord.active(
                newKeyId(),
                new EmailAddress(command.ownerEmail()),
                material.certificate().algorithm(),
                command.purpose(),
                keyProvider.name(),
                material.privateKeyProviderRef(),
                material.certificate().thumbprint());
        keyRecordRepository.save(subjectKey);
        return new ManagedCertificateMaterial(material.certificate(), subjectKey, material.publicKeyFormat());
    }

    @Override
    public CertificateCryptoPort.CertificateDescriptor signCsr(KeyProvider.SignCsrManagedCommand command, String issuerKeyId) {
        KeyRecord issuerKey = requireActiveKey(issuerKeyId);
        CertificateCryptoPort.CertificateDescriptor descriptor = provider(issuerKey).signCsr(command, issuerKey);
        markUsed(issuerKey);
        return descriptor;
    }

    @Override
    public CertificateCryptoPort.CrlContent generateCrl(KeyProvider.GenerateManagedCrlCommand command, String caKeyId) {
        KeyRecord caKey = requireActiveKey(caKeyId);
        CertificateCryptoPort.CrlContent content = provider(caKey).generateCrl(command, caKey);
        markUsed(caKey);
        return content;
    }

    private KeyRecord requireActiveKey(String keyId) {
        return keyRecordRepository.findById(keyId)
                .filter(KeyRecord::active)
                .orElseThrow(() -> new IllegalStateException("Managed key not found or disabled: " + keyId));
    }

    private void markUsed(KeyRecord keyRecord) {
        keyRecord.markUsed(Instant.now());
        keyRecordRepository.save(keyRecord);
    }

    private KeyProvider provider(KeyRecord keyRecord) {
        KeyProvider provider = providers.get(keyRecord.getProvider());
        if (provider == null) {
            throw new IllegalStateException("Unsupported key provider: " + keyRecord.getProvider());
        }
        return provider;
    }

    private KeyProvider defaultProvider() {
        KeyProvider provider = providers.get(LocalPkcs12KeyProvider.PROVIDER_NAME);
        if (provider == null) {
            throw new IllegalStateException("LOCAL_PKCS12 key provider is unavailable");
        }
        return provider;
    }

    private String newKeyId() {
        return "key-" + UUID.randomUUID();
    }
}
