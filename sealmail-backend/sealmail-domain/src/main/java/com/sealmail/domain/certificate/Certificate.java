package com.sealmail.domain.certificate;

import com.sealmail.domain.certificate.event.AliasAssigned;
import com.sealmail.domain.certificate.event.CertificateIssued;
import com.sealmail.domain.certificate.event.CertificateImported;
import com.sealmail.domain.certificate.event.CertificateRevoked;
import com.sealmail.domain.certificate.event.CertificateTrusted;
import com.sealmail.domain.certificate.event.CertificateUntrusted;
import com.sealmail.domain.certificate.event.KeyUsagesUpdated;
import com.sealmail.domain.mailsecurity.CryptoProfile;
import com.sealmail.domain.shared.exception.DomainException;
import com.sealmail.domain.shared.model.AggregateRoot;
import com.sealmail.domain.shared.model.EmailAddress;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public class Certificate extends AggregateRoot<CertificateId> {

    private final EmailAddress owner;
    private final String pemContent;
    private String alias;
    private final ValidityPeriod validity;
    private final Set<KeyUsage> keyUsages;
    private final String issuerDn;
    private final String subjectDn;
    private final BigInteger serialNumber;
    private final String subjectKeyIdentifier;
    private boolean trusted;
    private boolean revoked;
    private String revocationReason;
    private Instant revocationDate;
    private String revocationCrlReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private String privateKeySecretRef; // 关联私钥的外部 secret / keystore 引用
    private String algorithm; // 证书公钥算法（缓存，避免重复解析PEM）

    // ---- CA / chain ----
    private boolean ca;
    /** null = end-entity (BasicConstraints absent or CA=false); >=0 = pathLen for CA */
    private Integer pathLenConstraint;
    /** Thumbprint of the issuing certificate; null for self-signed root */
    private String issuerCertId;
    private final Set<String> extendedKeyUsages = new java.util.HashSet<>();
    private String crlDistributionPointUrl;
    private String importedCrlPem;

    private Certificate(CertificateId id, EmailAddress owner, String pemContent,
                       ValidityPeriod validity, Set<KeyUsage> keyUsages,
                       String issuerDn, String subjectDn, BigInteger serialNumber,
                       String subjectKeyIdentifier) {
        super(id);
        this.owner = owner;
        this.pemContent = pemContent;
        this.validity = validity;
        this.keyUsages = EnumSet.copyOf(keyUsages);
        this.issuerDn = issuerDn;
        this.subjectDn = subjectDn;
        this.serialNumber = serialNumber;
        this.subjectKeyIdentifier = subjectKeyIdentifier;
        this.trusted = false;
        this.revoked = false;
        this.createdAt = Instant.now();
    }

    public static Certificate importCertificate(CertificateId id, EmailAddress owner,
                                                 String pemContent, ValidityPeriod validity,
                                                 Set<KeyUsage> keyUsages, String issuerDn,
                                                 String subjectDn, BigInteger serialNumber,
                                                 String subjectKeyIdentifier) {
        Certificate cert = new Certificate(id, owner, pemContent, validity, keyUsages,
                issuerDn, subjectDn, serialNumber, subjectKeyIdentifier);
        cert.registerEvent(new CertificateImported(id, owner));
        return cert;
    }

    public static Certificate issueCertificate(CertificateId id, EmailAddress owner,
                                               String pemContent, ValidityPeriod validity,
                                               Set<KeyUsage> keyUsages, String issuerDn,
                                               String subjectDn, BigInteger serialNumber,
                                               String subjectKeyIdentifier) {
        Certificate cert = new Certificate(id, owner, pemContent, validity, keyUsages,
                issuerDn, subjectDn, serialNumber, subjectKeyIdentifier);
        cert.registerEvent(new CertificateIssued(id, owner));
        return cert;
    }

    public void trust() {
        if (trusted) {
            return;
        }
        this.trusted = true;
        registerEvent(new CertificateTrusted(getId()));
    }

    public void untrust() {
        if (!trusted) {
            return;
        }
        this.trusted = false;
        registerEvent(new CertificateUntrusted(getId()));
    }

    public void revoke(String reason) {
        revoke(reason, null, Instant.now());
    }

    public void revoke(String reason, String crlReasonCode, Instant when) {
        if (revoked) {
            throw new DomainException("Certificate is already revoked: " + getId());
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Revocation reason cannot be blank");
        }
        this.revoked = true;
        this.revocationReason = reason;
        this.revocationCrlReason = crlReasonCode;
        this.revocationDate = when != null ? when : Instant.now();
        registerEvent(new CertificateRevoked(getId(), reason));
    }

    public void assignAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be blank");
        }
        this.alias = alias;
        registerEvent(new AliasAssigned(getId(), alias));
    }

    public void updateKeyUsages(Set<KeyUsage> newUsages) {
        if (newUsages == null || newUsages.isEmpty()) {
            throw new IllegalArgumentException("Key usages cannot be empty");
        }
        this.keyUsages.clear();
        this.keyUsages.addAll(newUsages);
        registerEvent(new KeyUsagesUpdated(getId(), Collections.unmodifiableSet(keyUsages)));
    }

    public boolean isSuitableForEncryption() {
        return !revoked && !validity.isExpired() && keyUsages.contains(KeyUsage.ENCRYPTION) && trusted;
    }

    public boolean isSuitableForSigning() {
        return !revoked && !validity.isExpired() && keyUsages.contains(KeyUsage.SIGNING) && trusted;
    }

    public EmailAddress getOwner() {
        return owner;
    }

    public String getPemContent() {
        return pemContent;
    }

    public String getAlias() {
        return alias;
    }

    public ValidityPeriod getValidity() {
        return validity;
    }

    public Set<KeyUsage> getKeyUsages() {
        return Collections.unmodifiableSet(keyUsages);
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    public String getSubjectKeyIdentifier() {
        return subjectKeyIdentifier;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPrivateKeySecretRef() {
        return privateKeySecretRef;
    }

    public void setPrivateKeySecretRef(String privateKeySecretRef) {
        this.privateKeySecretRef = privateKeySecretRef;
    }

    public boolean hasPrivateKey() {
        return privateKeySecretRef != null && !privateKeySecretRef.isBlank();
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Optional<CryptoProfile> cryptoProfile() {
        return CryptoProfile.fromCertificateAlgorithm(algorithm);
    }

    public boolean isGmAlgorithmFamily() {
        return cryptoProfile().filter(CryptoProfile.GM::equals).isPresent();
    }

    public boolean isStandardAlgorithmFamily() {
        return cryptoProfile().filter(CryptoProfile.STANDARD::equals).isPresent();
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Instant getRevocationDate() {
        return revocationDate;
    }

    public void setRevocationDate(Instant revocationDate) {
        this.revocationDate = revocationDate;
    }

    public String getRevocationCrlReason() {
        return revocationCrlReason;
    }

    public void setRevocationCrlReason(String revocationCrlReason) {
        this.revocationCrlReason = revocationCrlReason;
    }

    public boolean isCA() {
        return ca;
    }

    public void markAsCA(int pathLenConstraint) {
        if (pathLenConstraint < 0) {
            throw new IllegalArgumentException("pathLenConstraint must be >= 0 for a CA");
        }
        this.ca = true;
        this.pathLenConstraint = pathLenConstraint;
    }

    public Integer getPathLenConstraint() {
        return pathLenConstraint;
    }

    public void setPathLenConstraint(Integer pathLenConstraint) {
        this.pathLenConstraint = pathLenConstraint;
    }

    public void setCA(boolean ca) {
        this.ca = ca;
    }

    public String getIssuerCertId() {
        return issuerCertId;
    }

    public void setIssuerCertId(String issuerCertId) {
        this.issuerCertId = issuerCertId;
    }

    public Set<String> getExtendedKeyUsages() {
        return Collections.unmodifiableSet(extendedKeyUsages);
    }

    public void setExtendedKeyUsages(Set<String> ekus) {
        this.extendedKeyUsages.clear();
        if (ekus != null) {
            this.extendedKeyUsages.addAll(ekus);
        }
    }

    public String getCrlDistributionPointUrl() {
        return crlDistributionPointUrl;
    }

    public void setCrlDistributionPointUrl(String crlDistributionPointUrl) {
        this.crlDistributionPointUrl = crlDistributionPointUrl;
    }

    public String getImportedCrlPem() {
        return importedCrlPem;
    }

    public void setImportedCrlPem(String importedCrlPem) {
        this.importedCrlPem = importedCrlPem;
    }

    public boolean hasImportedCrl() {
        return importedCrlPem != null && !importedCrlPem.isBlank();
    }
}
