package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.certificate.spi.SMIMEOperations;
import com.sealmail.domain.key.KeyOperationResult;
import com.sealmail.domain.key.KeyProvider;
import com.sealmail.domain.key.KeyRecord;
import org.springframework.stereotype.Component;

@Component
public class LocalPkcs12KeyProvider implements KeyProvider {

    public static final String PROVIDER_NAME = "LOCAL_PKCS12";

    private static final String CERTIFICATE_ALIAS_PREFIX = "certificate:";

    private final KeyStoreService keyStoreService;
    private final SMIMEOperations smimeOperations;
    private final BcCertificateCryptoPort certificateCryptoPort;

    public LocalPkcs12KeyProvider(KeyStoreService keyStoreService,
                                  SMIMEOperations smimeOperations,
                                  BcCertificateCryptoPort certificateCryptoPort) {
        this.keyStoreService = keyStoreService;
        this.smimeOperations = smimeOperations;
        this.certificateCryptoPort = certificateCryptoPort;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public String storePrivateKey(String ownerEmail,
                                  String certificateId,
                                  String certificatePem,
                                  String privateKeyPem) {
        String providerRef = alias(certificateId);
        keyStoreService.storePemKeyPair(providerRef, privateKeyPem, certificatePem);
        return providerRef;
    }

    @Override
    public boolean exists(String providerRef) {
        return providerRef != null && keyStoreService.getPrivateKeyByAlias(providerRef) != null;
    }

    @Override
    public KeyOperationResult signSmime(KeyRecord keyRecord, byte[] mimeMessage, String certificatePem) {
        byte[] signed = smimeOperations.sign(mimeMessage, privateKeyPem(keyRecord), certificatePem);
        return new KeyOperationResult(signed, keyRecord);
    }

    @Override
    public KeyOperationResult decryptSmime(KeyRecord keyRecord, byte[] encryptedMessage, String certificatePem) {
        byte[] decrypted = smimeOperations.decrypt(encryptedMessage, privateKeyPem(keyRecord), certificatePem);
        return new KeyOperationResult(decrypted, keyRecord);
    }

    @Override
    public ManagedCertificateMaterial issueSelfSigned(IssueSelfSignedManagedCommand command) {
        CertificateCryptoPort.CertificateMaterial material = certificateCryptoPort.issueSelfSigned(
                new CertificateCryptoPort.IssueSelfSignedCommand(
                        command.subjectDn(),
                        command.algorithm(),
                        command.validityDays(),
                        command.ca(),
                        command.pathLenConstraint(),
                        command.extendedKeyUsages(),
                        command.crlDistributionPointUrl()));
        String providerRef = storePrivateKey(
                command.ownerEmail(),
                material.certificate().thumbprint(),
                material.certificate().pemContent(),
                material.privateKeyPem());
        return new ManagedCertificateMaterial(material.certificate(), providerRef, material.publicKeyFormat());
    }

    @Override
    public ManagedCertificateMaterial issueWithIssuer(IssueWithManagedIssuerCommand command, KeyRecord issuerKey) {
        CertificateCryptoPort.CertificateMaterial material = certificateCryptoPort.issueWithIssuer(
                new CertificateCryptoPort.IssueWithIssuerCommand(
                        command.subjectDn(),
                        command.subjectAlgorithm(),
                        command.issuerCertificatePem(),
                        privateKeyPem(issuerKey),
                        command.validityDays(),
                        command.ca(),
                        command.pathLenConstraint(),
                        command.extendedKeyUsages(),
                        command.crlDistributionPointUrl()));
        String providerRef = storePrivateKey(
                command.ownerEmail(),
                material.certificate().thumbprint(),
                material.certificate().pemContent(),
                material.privateKeyPem());
        return new ManagedCertificateMaterial(material.certificate(), providerRef, material.publicKeyFormat());
    }

    @Override
    public CertificateCryptoPort.CertificateDescriptor signCsr(SignCsrManagedCommand command, KeyRecord issuerKey) {
        return certificateCryptoPort.signCsr(new CertificateCryptoPort.SignCsrCommand(
                command.csrPem(),
                command.issuerCertificatePem(),
                privateKeyPem(issuerKey),
                command.validityDays(),
                command.crlDistributionPointUrl()));
    }

    @Override
    public CertificateCryptoPort.CrlContent generateCrl(GenerateManagedCrlCommand command, KeyRecord caKey) {
        return certificateCryptoPort.generateCrl(new CertificateCryptoPort.GenerateCrlCommand(
                command.caCertificatePem(),
                privateKeyPem(caKey),
                command.thisUpdate(),
                command.nextUpdate(),
                command.revokedEntries()));
    }

    private String privateKeyPem(KeyRecord keyRecord) {
        String pem = keyStoreService.getPrivateKeyPemByAlias(keyRecord.getProviderRef());
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Managed key material is unavailable: " + keyRecord.getKeyId());
        }
        return pem;
    }

    static String alias(String certificateId) {
        return CERTIFICATE_ALIAS_PREFIX + certificateId;
    }
}
