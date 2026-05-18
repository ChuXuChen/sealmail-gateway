package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.dto.response.CrlContentResponse;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.certificate.spi.CertificateCryptoPort;
import com.sealmail.domain.key.KeyManagementPort;
import com.sealmail.domain.key.KeyProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Generate a PKIX-compliant CRL for one CA, signed with its private key. The CRL
 * enumerates every certificate stored in our gateway whose {@code issuerCertId}
 * points at this CA and whose {@code revoked} flag is true.
 *
 * The CRL has CRLNumber and AuthorityKeyIdentifier extensions. Validity window
 * is "now → now + 1 day"; this is a lightweight internal CA — we regenerate on
 * every request rather than maintaining a CRL number sequence in DB.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerateCrlUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateCrlUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateCryptoPort certificateCryptoPort;
    private final CertificatePrivateKeyMaterialService privateKeyMaterialService;
    private final KeyManagementPort keyManagementPort;

    public CrlContentResponse execute(String caCertId) {
        CertificateCryptoPort.CrlContent crl = generate(caCertId);
        return new CrlContentResponse(crl.der(), crl.pem());
    }

    private CertificateCryptoPort.CrlContent generate(String caCertId) {
        Certificate caCert = certificateRepository.findById(new CertificateId(caCertId))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", caCertId));
        if (!caCert.isCA()) {
            throw BusinessException.badRequest("证书不是 CA，没有 CRL");
        }
        if (caCert.hasImportedCrl()) {
            try {
                return certificateCryptoPort.normalizeAndValidateCrl(caCert.getPemContent(), caCert.getImportedCrlPem(), null);
            } catch (Exception e) {
                log.warn("Imported CRL for CA {} is invalid, falling back to dynamic CRL: {}",
                        caCertId, e.getMessage());
            }
        }
        if (!caCert.hasPrivateKey()) {
            throw BusinessException.badRequest("CA 没有关联私钥，无法签发 CRL");
        }

        try {
            List<Certificate> children = certificateRepository.findByIssuerCertId(caCertId);
            Instant now = Instant.now();
            CertificateCryptoPort.CrlContent crl = keyManagementPort.generateCrl(
                    new KeyProvider.GenerateManagedCrlCommand(
                            caCert.getPemContent(),
                            now,
                            now.plusSeconds(24L * 60 * 60),
                            children.stream()
                                    .filter(Certificate::isRevoked)
                                    .map(this::toCrlEntry)
                                    .filter(java.util.Objects::nonNull)
                                    .toList()),
                    privateKeyMaterialService.requireManagedKey(caCert, "CA 没有关联私钥，无法签发 CRL").getKeyId());

            log.info("Generated CRL for CA {} ({} revoked entries)",
                    caCertId, children.stream().filter(Certificate::isRevoked).count());
            return crl;

        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate CRL for CA {}: {}", caCertId, e.getMessage(), e);
            throw new RuntimeException("CRL 生成失败: " + e.getMessage(), e);
        }
    }

    private CertificateCryptoPort.CrlEntry toCrlEntry(Certificate child) {
        try {
            return new CertificateCryptoPort.CrlEntry(
                    new BigInteger(child.getSerialNumber().toString()),
                    child.getRevocationDate(),
                    child.getRevocationCrlReason());
        } catch (Exception e) {
            return null;
        }
    }
}
