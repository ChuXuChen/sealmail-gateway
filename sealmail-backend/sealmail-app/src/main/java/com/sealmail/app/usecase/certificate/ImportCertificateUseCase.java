package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.ImportCertificateRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.CertificateException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.KeyUsage;
import com.sealmail.domain.certificate.ValidityPeriod;
import com.sealmail.domain.certificate.spi.CertificateValidator;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportCertificateUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportCertificateUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateValidator certificateValidator;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoService cryptoService;

    @Transactional
    public CertificateResponse execute(ImportCertificateRequest request, UserContext user) {
        EmailAddress owner = new EmailAddress(request.getOwnerEmail());

        CertificateValidator.ValidationResult validation =
                certificateValidator.validate(request.getPemData());
        if (!validation.isValid()) {
            throw CertificateException.invalidCertificate(
                    "Certificate validation failed: " + validation.reason());
        }

        try {
            X509Certificate x509 = cryptoService.parseCertificate(request.getPemData());
            int basicConstraints = x509.getBasicConstraints();
            if (basicConstraints >= 0) {
                permissionChecker.checkCanManageCa(user);
            } else {
                permissionChecker.checkCanManageCertificates(user, owner.getDomain());
            }

            String algorithm = mapPublicKeyAlgorithm(x509.getPublicKey().getAlgorithm());
            String thumbprint = cryptoService.computeThumbprint(x509);
            CertificateId certId = new CertificateId(thumbprint);

            if (certificateRepository.findById(certId).isPresent()) {
                throw CertificateException.invalidCertificate(
                        "Certificate already exists with thumbprint: " + thumbprint);
            }

            ValidityPeriod validity = new ValidityPeriod(
                    Instant.ofEpochMilli(x509.getNotBefore().getTime()),
                    Instant.ofEpochMilli(x509.getNotAfter().getTime()));

            Set<KeyUsage> usages = extractKeyUsages(x509);
            String ski = cryptoService.extractSubjectKeyIdentifier(x509);

            Certificate cert = Certificate.importCertificate(
                    certId,
                    owner,
                    request.getPemData(),
                    validity,
                    usages,
                    x509.getIssuerX500Principal().getName(),
                    x509.getSubjectX500Principal().getName(),
                    x509.getSerialNumber(),
                    ski);

            cert.setAlgorithm(algorithm);
            if (basicConstraints >= 0) {
                cert.markAsCA(basicConstraints == Integer.MAX_VALUE ? 0 : basicConstraints);
            }
            String issuerCertId = resolveIssuerCertId(x509, thumbprint);
            if (issuerCertId != null) {
                cert.setIssuerCertId(issuerCertId);
            }
            cert.setCrlDistributionPointUrl(cryptoService.extractCrlDistributionPointUrl(x509));

            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            // Import defaults to NOT trusted (admin must explicitly trust unknown certs).
            if (Boolean.TRUE.equals(request.getTrusted())) {
                cert.trust();
            }
            if (request.getPrivateKeyData() != null && !request.getPrivateKeyData().isBlank()) {
                cryptoService.validateCertificateMatchesPrivateKey(
                        x509, cryptoService.parsePrivateKey(request.getPrivateKeyData()));
                cert.setPrivateKeyData(request.getPrivateKeyData());
            }

            certificateRepository.save(cert);
            log.info("User [{}] imported {} certificate {} owner={}",
                    user.getUserId(), algorithm, thumbprint, request.getOwnerEmail());

            return mapper.toResponse(cert);

        } catch (IllegalArgumentException e) {
            throw CertificateException.invalidCertificate(e.getMessage());
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to import certificate: {}", e.getMessage(), e);
            throw CertificateException.importFailed(e.getMessage(), e);
        }
    }

    private String resolveIssuerCertId(X509Certificate importedCert, String importedThumbprint) {
        if (cryptoService.isSelfSigned(importedCert)) {
            return null;
        }

        return certificateRepository.findAllCAs().stream()
                .filter(candidate -> !candidate.getId().getThumbprint().equals(importedThumbprint))
                .filter(candidate -> {
                    try {
                        X509Certificate issuer = cryptoService.parseCertificate(candidate.getPemContent());
                        return cryptoService.isIssuedBy(importedCert, issuer);
                    } catch (Exception e) {
                        log.debug("Skipping issuer candidate {}: {}",
                                candidate.getId().getThumbprint(), e.getMessage());
                        return false;
                    }
                })
                .findFirst()
                .map(candidate -> candidate.getId().getThumbprint())
                .orElse(null);
    }

    /**
     * Resolve the X.509 keyUsage extension to our enum. RFC 5280: digitalSignature ->
     * SIGNING; keyEncipherment / keyAgreement / dataEncipherment -> ENCRYPTION. If
     * the extension is absent we default to BOTH so the imported cert is usable for
     * either side of S/MIME.
     */
    private Set<KeyUsage> extractKeyUsages(X509Certificate x509) {
        boolean[] keyUsage = x509.getKeyUsage();
        if (keyUsage == null) {
            return EnumSet.of(KeyUsage.SIGNING, KeyUsage.ENCRYPTION);
        }
        Set<KeyUsage> usages = EnumSet.noneOf(KeyUsage.class);
        // bit 0 = digitalSignature, bit 1 = nonRepudiation, bit 2 = keyEncipherment,
        // bit 3 = dataEncipherment, bit 4 = keyAgreement
        if (keyUsage.length > 0 && keyUsage[0]) usages.add(KeyUsage.SIGNING);
        if (keyUsage.length > 1 && keyUsage[1]) usages.add(KeyUsage.SIGNING);
        if (keyUsage.length > 2 && keyUsage[2]) usages.add(KeyUsage.ENCRYPTION);
        if (keyUsage.length > 3 && keyUsage[3]) usages.add(KeyUsage.ENCRYPTION);
        if (keyUsage.length > 4 && keyUsage[4]) usages.add(KeyUsage.ENCRYPTION);
        if (usages.isEmpty()) {
            usages.add(KeyUsage.SIGNING);
        }
        return usages;
    }

    private String mapPublicKeyAlgorithm(String javaAlg) {
        if (javaAlg == null) return "UNKNOWN";
        return switch (javaAlg.toUpperCase()) {
            case "EC", "ECDSA", "SM2" -> "SM2";
            case "RSA" -> "RSA";
            default -> javaAlg;
        };
    }
}
