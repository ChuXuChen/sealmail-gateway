package com.sealmail.app.usecase.certificate;

import com.sealmail.app.dto.request.SignCsrRequest;
import com.sealmail.app.dto.response.CertificateResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.exception.ResourceNotFoundException;
import com.sealmail.app.mapper.CertificateDtoMapper;
import com.sealmail.app.security.PermissionChecker;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.certificate.Certificate;
import com.sealmail.domain.certificate.CertificateId;
import com.sealmail.domain.certificate.CertificateRepository;
import com.sealmail.domain.shared.model.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Service
@RequiredArgsConstructor
@Transactional
public class SignCsrUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignCsrUseCase.class);

    private final CertificateRepository certificateRepository;
    private final CertificateDtoMapper mapper;
    private final PermissionChecker permissionChecker;
    private final CertificateCryptoService cryptoService;
    private final CertificateChainService certificateChainService;

    @Value("${sealmail.ca.crl-base-url:http://localhost:8080/api/v1/crl/}")
    private String crlBaseUrl;

    public CertificateResponse execute(SignCsrRequest request, UserContext user) {
        Certificate caCert = certificateRepository.findById(new CertificateId(request.getCaCertId()))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", request.getCaCertId()));
        if (!caCert.isCA() || caCert.getPathLenConstraint() == null || caCert.getPathLenConstraint() != 0) {
            throw BusinessException.badRequest("CSR 必须由 Intermediate CA (pathLen=0) 签发");
        }
        if (!caCert.hasPrivateKey()) {
            throw BusinessException.badRequest("所选 CA 证书没有关联私钥，无法签发");
        }
        if (caCert.isRevoked()) {
            throw BusinessException.badRequest("所选 CA 证书已吊销，无法签发");
        }
        if (!certificateChainService.isChainTrustedAndUsable(caCert)) {
            throw BusinessException.badRequest("所选 CA 证书链不可信或已失效，无法签发");
        }

        try {
            PKCS10CertificationRequest csr = cryptoService.parseCsr(request.getCsrPem());
            cryptoService.validateCsr(csr);
            EmailAddress owner = extractEmail(csr.getSubject());
            if (owner == null) {
                throw BusinessException.badRequest("CSR 的 Subject 中未找到 emailAddress 或可识别的邮箱");
            }
            permissionChecker.checkCanManageCertificates(user, owner.getDomain());

            PrivateKey caPriv = cryptoService.parsePrivateKey(caCert.getPrivateKeyData());
            X509Certificate caX509 = cryptoService.parseCertificate(caCert.getPemContent());

            int validity = request.getValidityDays() != null ? request.getValidityDays() : 365;
            String crlUrl = buildCrlUrl(caCert.getId().getThumbprint());
            X509Certificate signed = cryptoService.signCsr(csr, caX509, caPriv, validity, crlUrl);

            String thumbprint = cryptoService.computeThumbprint(signed);
            CertificateId certId = new CertificateId(thumbprint);
            if (certificateRepository.findById(certId).isPresent()) {
                throw BusinessException.badRequest("相同指纹的证书已存在: " + thumbprint);
            }

            String algorithm = cryptoService.detectAlgorithm(csr.getSubjectPublicKeyInfo());
            Certificate cert = cryptoService.toIssuedDomainCertificate(certId, owner, signed, algorithm);
            cert.setIssuerCertId(caCert.getId().getThumbprint());
            cert.setCrlDistributionPointUrl(crlUrl);
            // CSR signing: requestor holds the private key, we don't store it here.
            if (request.getAlias() != null && !request.getAlias().isBlank()) {
                cert.assignAlias(request.getAlias());
            }
            if (!Boolean.FALSE.equals(request.getTrusted())) {
                cert.trust();
            }
            certificateRepository.save(cert);

            log.info("User [{}] signed CSR -> certificate {} for {} (CA={})",
                    user.getUserId(), thumbprint, owner.getValue(), request.getCaCertId());

            return mapper.toResponse(cert);

        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(e.getMessage());
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to sign CSR: {}", e.getMessage(), e);
            throw new RuntimeException("CSR 签发失败: " + e.getMessage(), e);
        }
    }

    private String buildCrlUrl(String caId) {
        String prefix = crlBaseUrl;
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        return prefix + caId;
    }

    private EmailAddress extractEmail(X500Name subject) {
        // RFC 2985 emailAddress attribute or CN that contains an '@'.
        for (RDN rdn : subject.getRDNs(BCStyle.EmailAddress)) {
            String email = readRdn(rdn);
            if (email != null) return new EmailAddress(email);
        }
        for (RDN rdn : subject.getRDNs(BCStyle.E)) {
            String email = readRdn(rdn);
            if (email != null) return new EmailAddress(email);
        }
        for (RDN rdn : subject.getRDNs(BCStyle.CN)) {
            String cn = readRdn(rdn);
            if (cn != null && cn.contains("@")) {
                return new EmailAddress(cn);
            }
        }
        return null;
    }

    private String readRdn(RDN rdn) {
        ASN1Encodable enc = rdn.getFirst() == null ? null : rdn.getFirst().getValue();
        if (enc == null) return null;
        return IETFUtils.valueToString(enc);
    }
}
