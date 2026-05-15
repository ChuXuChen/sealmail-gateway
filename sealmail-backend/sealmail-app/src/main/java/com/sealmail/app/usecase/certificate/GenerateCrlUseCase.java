package com.sealmail.app.usecase.certificate;

import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
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
    private final CertificateCryptoService cryptoService;

    public X509CRL execute(String caCertId) {
        Certificate caCert = certificateRepository.findById(new CertificateId(caCertId))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", caCertId));
        if (!caCert.isCA()) {
            throw BusinessException.badRequest("证书不是 CA，没有 CRL");
        }
        if (caCert.hasImportedCrl()) {
            try {
                return cryptoService.parseCrl(caCert.getImportedCrlPem());
            } catch (Exception e) {
                log.warn("Imported CRL for CA {} is invalid, falling back to dynamic CRL: {}",
                        caCertId, e.getMessage());
            }
        }
        if (!caCert.hasPrivateKey()) {
            throw BusinessException.badRequest("CA 没有关联私钥，无法签发 CRL");
        }

        try {
            X509Certificate caX509 = cryptoService.parseCertificate(caCert.getPemContent());
            PrivateKey caPriv = cryptoService.parsePrivateKey(caCert.getPrivateKeyData());

            Date now = new Date();
            Date nextUpdate = new Date(now.getTime() + 24L * 60 * 60 * 1000);

            X509v2CRLBuilder builder = new X509v2CRLBuilder(
                    new X500Name(caX509.getSubjectX500Principal().getName()), now);
            builder.setNextUpdate(nextUpdate);

            // CRLNumber: use seconds-since-epoch for a stable, monotonic value.
            builder.addExtension(Extension.cRLNumber, false,
                    new org.bouncycastle.asn1.x509.CRLNumber(BigInteger.valueOf(now.getTime() / 1000)));

            // Authority Key Identifier
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(caX509.getPublicKey()));

            List<Certificate> children = certificateRepository.findByIssuerCertId(caCertId);
            for (Certificate child : children) {
                if (!child.isRevoked()) continue;
                BigInteger serial = parseSerial(child);
                if (serial == null) continue;
                Date revDate = child.getRevocationDate() != null
                        ? Date.from(child.getRevocationDate())
                        : new Date();
                int reasonCode = mapReason(child.getRevocationCrlReason());
                builder.addCRLEntry(serial, revDate, reasonCode);
            }

            String sigAlg = "EC".equalsIgnoreCase(caPriv.getAlgorithm())
                    || "ECDSA".equalsIgnoreCase(caPriv.getAlgorithm())
                    ? "SM3withSM2" : "SHA256withRSA";
            ContentSigner signer = new JcaContentSignerBuilder(sigAlg).setProvider("BC").build(caPriv);
            X509CRLHolder holder = builder.build(signer);
            X509CRL crl = new JcaX509CRLConverter().setProvider("BC").getCRL(holder);

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

    public String toPem(X509CRL crl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN X509 CRL-----\n");
            String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(crl.getEncoded());
            sb.append(b64);
            if (!b64.endsWith("\n")) sb.append('\n');
            sb.append("-----END X509 CRL-----\n");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode CRL to PEM", e);
        }
    }

    private BigInteger parseSerial(Certificate child) {
        try {
            return new BigInteger(child.getSerialNumber().toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** Map our string reason code to RFC 5280 CRL Reason integer; unknown -> unspecified (0). */
    private int mapReason(String reasonCode) {
        if (reasonCode == null) return CRLReason.unspecified;
        return switch (reasonCode.toUpperCase()) {
            case "KEY_COMPROMISE" -> CRLReason.keyCompromise;
            case "CA_COMPROMISE" -> CRLReason.cACompromise;
            case "AFFILIATION_CHANGED" -> CRLReason.affiliationChanged;
            case "SUPERSEDED" -> CRLReason.superseded;
            case "CESSATION_OF_OPERATION" -> CRLReason.cessationOfOperation;
            case "CERTIFICATE_HOLD" -> CRLReason.certificateHold;
            case "PRIVILEGE_WITHDRAWN" -> CRLReason.privilegeWithdrawn;
            case "AA_COMPROMISE" -> CRLReason.aACompromise;
            default -> CRLReason.unspecified;
        };
    }
}
