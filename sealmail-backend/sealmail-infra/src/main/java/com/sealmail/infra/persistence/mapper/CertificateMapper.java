package com.sealmail.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.key.KeyRecord;
import com.sealmail.domain.shared.model.EmailAddress;
import com.sealmail.infra.persistence.entity.CertificateEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CertificateMapper {

    private static final Logger log = LoggerFactory.getLogger(CertificateMapper.class);

    private final ObjectMapper objectMapper;

    public CertificateMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CertificateEntity toEntity(Certificate certificate) {
        CertificateEntity entity = new CertificateEntity();
        entity.setThumbprint(certificate.getId().getThumbprint());
        entity.setOwnerEmail(certificate.getOwner().getValue());
        entity.setPemContent(certificate.getPemContent());
        entity.setAlias(certificate.getAlias());
        entity.setNotBefore(certificate.getValidity().getNotBefore());
        entity.setNotAfter(certificate.getValidity().getNotAfter());
        try {
            entity.setKeyUsages(objectMapper.writeValueAsString(
                    certificate.getKeyUsages().stream()
                            .map(KeyUsage::name)
                            .collect(Collectors.toSet())
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize key usages", e);
        }
        entity.setIssuerDn(certificate.getIssuerDn());
        entity.setSubjectDn(certificate.getSubjectDn());
        entity.setSerialNumber(certificate.getSerialNumber().toString());
        entity.setSubjectKeyIdentifier(certificate.getSubjectKeyIdentifier());
        entity.setTrusted(certificate.isTrusted());
        entity.setRevoked(certificate.isRevoked());
        entity.setRevocationReason(certificate.getRevocationReason());
        entity.setRevocationDate(certificate.getRevocationDate());
        entity.setRevocationCrlReason(certificate.getRevocationCrlReason());
        entity.setCreatedAt(certificate.getCreatedAt());
        entity.setUpdatedAt(certificate.getUpdatedAt());
        entity.setPrivateKeySecretRef(certificate.getPrivateKeySecretRef());
        entity.setHasPrivateKey(certificate.hasPrivateKey());
        entity.setAlgorithm(certificate.getAlgorithm());

        entity.setCa(certificate.isCA());
        entity.setPathLenConstraint(certificate.getPathLenConstraint());
        entity.setIssuerCertId(certificate.getIssuerCertId());
        entity.setCrlDpUrl(certificate.getCrlDistributionPointUrl());
        entity.setImportedCrlPem(certificate.getImportedCrlPem());
        try {
            entity.setExtendedKeyUsages(objectMapper.writeValueAsString(certificate.getExtendedKeyUsages()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize EKU set", e);
        }
        return entity;
    }

    public Certificate toDomain(CertificateEntity entity) {
        Set<String> keyUsageStrings;
        try {
            try {
                keyUsageStrings = objectMapper.readValue(entity.getKeyUsages(), new TypeReference<Set<String>>() {});
            } catch (JsonProcessingException e) {
                // Handle double-encoded JSON
                String actualJson = objectMapper.readValue(entity.getKeyUsages(), String.class);
                keyUsageStrings = objectMapper.readValue(actualJson, new TypeReference<Set<String>>() {});
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize key usages: " + entity.getKeyUsages(), e);
        }
        Set<KeyUsage> keyUsages = keyUsageStrings.stream()
                .map(KeyUsage::valueOf)
                .collect(Collectors.toSet());

        ValidityPeriod validity = new ValidityPeriod(entity.getNotBefore(), entity.getNotAfter());
        CertificateId id = new CertificateId(entity.getThumbprint());
        EmailAddress owner = new EmailAddress(entity.getOwnerEmail());

        Certificate cert = Certificate.importCertificate(
                id, owner, entity.getPemContent(), validity, keyUsages,
                entity.getIssuerDn(), entity.getSubjectDn(),
                new BigInteger(entity.getSerialNumber()),
                entity.getSubjectKeyIdentifier()
        );

        // Restore alias (assignAlias rejects blank, so guard).
        if (entity.getAlias() != null && !entity.getAlias().isBlank()) {
            cert.assignAlias(entity.getAlias());
        }

        // Restore state (since we're reconstructing from persistence
        if (entity.isTrusted()) {
            cert.trust();
        }
        if (entity.isRevoked()) {
            try {
                cert.revoke(
                        entity.getRevocationReason(),
                        entity.getRevocationCrlReason(),
                        entity.getRevocationDate());
            } catch (RuntimeException e) {
                log.debug("Skipped invalid persisted certificate revocation state for {}: {}",
                        entity.getThumbprint(), e.getMessage());
            }
        }

        cert.setCA(entity.isCa());
        cert.setPathLenConstraint(entity.getPathLenConstraint());
        cert.setIssuerCertId(entity.getIssuerCertId());
        cert.setCrlDistributionPointUrl(entity.getCrlDpUrl());
        cert.setImportedCrlPem(entity.getImportedCrlPem());
        if (entity.getExtendedKeyUsages() != null && !entity.getExtendedKeyUsages().isBlank()) {
            try {
                Set<String> ekus = objectMapper.readValue(
                        entity.getExtendedKeyUsages(), new TypeReference<Set<String>>() {});
                cert.setExtendedKeyUsages(ekus);
            } catch (JsonProcessingException e) {
                log.debug("Skipped invalid persisted certificate EKU JSON for {}: {}",
                        entity.getThumbprint(), e.getOriginalMessage());
            }
        }

        if (entity.getPrivateKeySecretRef() != null && !entity.getPrivateKeySecretRef().isBlank()) {
            cert.setPrivateKeySecretRef(normalizePrivateKeyRef(entity.getPrivateKeySecretRef()));
        }

        // 恢复算法缓存
        if (entity.getAlgorithm() != null && !entity.getAlgorithm().isBlank()) {
            cert.setAlgorithm(entity.getAlgorithm());
        }

        // 恢复更新时间
        if (entity.getUpdatedAt() != null) {
            cert.setUpdatedAt(entity.getUpdatedAt());
        }

        cert.clearDomainEvents();
        return cert;
    }

    private String normalizePrivateKeyRef(String ref) {
        String value = ref.trim();
        if (value.startsWith(KeyRecord.MANAGED_KEY_REF_PREFIX)) {
            return value;
        }
        if (value.startsWith("keystore:certificate:")) {
            return KeyRecord.MANAGED_KEY_REF_PREFIX
                    + value.substring("keystore:certificate:".length());
        }
        return value;
    }
}
