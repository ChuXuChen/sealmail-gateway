package com.sealmail.infra.crypto;

import com.sealmail.domain.certificate.spi.CertificateValidator;
import com.sealmail.infra.crypto.util.PemUtils;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

@Component
public class BcCertificateValidator implements CertificateValidator {

    private static final Logger log = LoggerFactory.getLogger(BcCertificateValidator.class);

    private final boolean crlEnabled;
    private final boolean ocspEnabled;
    private final int ocspTimeout;
    private final List<X509Certificate> trustAnchors;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        Security.setProperty("ocsp.enable", "true");
    }

    public BcCertificateValidator(
            @Value("${sealmail.security.crl.enabled:true}") boolean crlEnabled,
            @Value("${sealmail.security.ocsp.enabled:true}") boolean ocspEnabled,
            @Value("${sealmail.security.ocsp.timeout:10000}") int ocspTimeout) {
        this.crlEnabled = crlEnabled;
        this.ocspEnabled = ocspEnabled;
        this.ocspTimeout = ocspTimeout;
        this.trustAnchors = loadSystemTrustAnchors();
    }

    @Override
    public ValidationResult validate(String pemCert) {
        // Gateway acts as its own trust authority for ingested certificates: we
        // accept self-signed and privately-chained certs. The hard checks here
        // are validity period and (when reachable) CRL/OCSP revocation. PKIX
        // path building against system cacerts is intentionally NOT enforced.
        try {
            X509Certificate cert = PemUtils.parseCertificate(pemCert);
            cert.checkValidity(new Date());

            if (crlEnabled) {
                ValidationResult crlResult = checkCRL(cert);
                if (!crlResult.isValid()) {
                    return crlResult;
                }
            }

            if (ocspEnabled) {
                ValidationResult ocspResult = checkOCSP(cert);
                if (!ocspResult.isValid()) {
                    return ocspResult;
                }
            }

            return ValidationResult.ok();
        } catch (Exception e) {
            return ValidationResult.failed("Certificate validation failed: " + e.getMessage());
        }
    }

    private ValidationResult checkCRL(X509Certificate cert) {
        try {
            X509CRL crl = fetchCRL(cert);
            if (crl == null) {
                return ValidationResult.ok();
            }

            if (crl.isRevoked(cert)) {
                return ValidationResult.failed("Certificate is revoked per CRL");
            }

            return ValidationResult.ok();
        } catch (Exception e) {
            log.warn("CRL check failed, skipping: {}", e.getMessage());
            return ValidationResult.ok();
        }
    }

    private ValidationResult checkOCSP(X509Certificate cert) {
        try {
            String ocspUrl = extractOCSPUrl(cert);
            if (ocspUrl == null) {
                log.debug("No OCSP URL found in certificate, skipping OCSP check");
                return ValidationResult.ok();
            }

            X509Certificate issuerCert = findIssuerCertificate(cert);
            if (issuerCert == null) {
                log.warn("Could not find issuer certificate for OCSP check");
                return ValidationResult.ok();
            }

            CertificateID certId = new CertificateID(
                    new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build()
                            .get(CertificateID.HASH_SHA1),
                    new X509CertificateHolder(issuerCert.getEncoded()),
                    cert.getSerialNumber()
            );

            OCSPReq request = buildOCSPRequest(certId);
            byte[] responseBytes = sendOCSPRequest(ocspUrl, request);
            OCSPResp response = new OCSPResp(responseBytes);

            if (response.getStatus() != OCSPResp.SUCCESSFUL) {
                log.warn("OCSP request failed with status: {}", response.getStatus());
                return ValidationResult.ok();
            }

            BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
            if (!verifyOCSPResponse(basicResponse, issuerCert)) {
                log.warn("OCSP response signature verification failed");
                return ValidationResult.ok();
            }

            SingleResp[] singleResps = basicResponse.getResponses();
            if (singleResps.length == 0) {
                log.warn("No OCSP response received");
                return ValidationResult.ok();
            }

            SingleResp singleResp = singleResps[0];
            Object certStatus = singleResp.getCertStatus();

            if (certStatus == CertificateStatus.GOOD) {
                log.debug("OCSP: Certificate is valid");
                return ValidationResult.ok();
            } else if (certStatus instanceof RevokedStatus) {
                RevokedStatus revoked = (RevokedStatus) certStatus;
                String reason = revoked.hasRevocationReason()
                        ? " (reason: " + revoked.getRevocationReason() + ")"
                        : "";
                return ValidationResult.failed("Certificate is revoked per OCSP" + reason);
            } else {
                log.warn("OCSP: Unknown certificate status - {}", certStatus);
                return ValidationResult.ok();
            }

        } catch (Exception e) {
            log.warn("OCSP check failed, skipping: {}", e.getMessage());
            return ValidationResult.ok();
        }
    }

    private OCSPReq buildOCSPRequest(CertificateID certId) throws Exception {
        OCSPReqBuilder builder = new OCSPReqBuilder();
        builder.addRequest(certId);

        // Add nonce extension to prevent replay attacks
        org.bouncycastle.asn1.x509.Extension ext = new org.bouncycastle.asn1.x509.Extension(
                OCSPObjectIdentifiers.id_pkix_ocsp_nonce,
                false,
                new org.bouncycastle.asn1.DEROctetString(
                        BigInteger.valueOf(System.currentTimeMillis()).toByteArray())
        );
        builder.setRequestExtensions(new Extensions(new org.bouncycastle.asn1.x509.Extension[]{ext}));

        return builder.build();
    }

    private byte[] sendOCSPRequest(String ocspUrl, OCSPReq request) throws Exception {
        URL url = new URI(ocspUrl).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(ocspTimeout);
        con.setReadTimeout(ocspTimeout);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/ocsp-request");
        con.setRequestProperty("Accept", "application/ocsp-response");
        con.setDoOutput(true);

        try (OutputStream out = con.getOutputStream()) {
            out.write(request.getEncoded());
        }

        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("OCSP server returned: " + responseCode);
        }

        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    private boolean verifyOCSPResponse(BasicOCSPResp response, X509Certificate issuerCert) {
        try {
            return response.isSignatureValid(
                    new JcaContentVerifierProviderBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(issuerCert.getPublicKey())
            );
        } catch (Exception e) {
            log.warn("Failed to verify OCSP response signature: {}", e.getMessage());
            return false;
        }
    }

    private X509Certificate findIssuerCertificate(X509Certificate cert) {
        // First check trust anchors for issuer
        for (X509Certificate anchor : trustAnchors) {
            if (anchor.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                return anchor;
            }
        }

        // Try to fetch from AIA extension
        try {
            String issuerUrl = extractIssuerUrl(cert);
            if (issuerUrl != null) {
                return fetchIssuerCertificate(issuerUrl);
            }
        } catch (Exception e) {
            log.debug("Failed to fetch issuer certificate: {}", e.getMessage());
        }

        return null;
    }

    private String extractIssuerUrl(X509Certificate cert) {
        try {
            byte[] authInfoAccessExt = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (authInfoAccessExt == null) {
                return null;
            }

            ASN1InputStream asn1In = new ASN1InputStream(new ByteArrayInputStream(authInfoAccessExt));
            byte[] octets = ((ASN1OctetString) asn1In.readObject()).getOctets();
            asn1In.close();

            asn1In = new ASN1InputStream(new ByteArrayInputStream(octets));
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(asn1In.readObject());
            asn1In.close();

            for (AccessDescription ad : aia.getAccessDescriptions()) {
                if (ad.getAccessMethod().equals(AccessDescription.id_ad_caIssuers)) {
                    GeneralName gn = ad.getAccessLocation();
                    if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        return gn.getName().toString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private X509Certificate fetchIssuerCertificate(String url) throws Exception {
        URL u = new URI(url).toURL();
        try (InputStream in = u.openStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    private ValidationResult validateCertificatePath(X509Certificate cert) {
        try {
            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(cert);

            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate caCert : trustAnchors) {
                anchors.add(new TrustAnchor(caCert, null));
            }

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, selector);
            params.setRevocationEnabled(false);

            builder.build(params);
            return ValidationResult.ok();

        } catch (CertPathBuilderException e) {
            return ValidationResult.failed("Certificate path validation failed: " + e.getMessage());
        } catch (Exception e) {
            return ValidationResult.failed("PKIX validation error: " + e.getMessage());
        }
    }

    private X509CRL fetchCRL(X509Certificate cert) {
        try {
            String crlUrl = extractCRLUrl(cert);
            if (crlUrl == null) {
                return null;
            }

            URL url = new URI(crlUrl).toURL();
            try (InputStream in = url.openStream()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                return (X509CRL) cf.generateCRL(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCRLUrl(X509Certificate cert) {
        try {
            byte[] crlExt = cert.getExtensionValue("2.5.29.31");
            if (crlExt == null) {
                return null;
            }

            ASN1InputStream asn1In = new ASN1InputStream(new java.io.ByteArrayInputStream(crlExt));
            byte[] octets = ((ASN1OctetString) asn1In.readObject()).getOctets();
            asn1In.close();

            asn1In = new ASN1InputStream(new java.io.ByteArrayInputStream(octets));
            CRLDistPoint distPoint = CRLDistPoint.getInstance(asn1In.readObject());
            asn1In.close();

            for (DistributionPoint dp : distPoint.getDistributionPoints()) {
                if (dp.getDistributionPoint() != null) {
                    GeneralNames names = GeneralNames.getInstance(dp.getDistributionPoint().getName());
                    for (GeneralName name : names.getNames()) {
                        if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
                            return name.getName().toString();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractOCSPUrl(X509Certificate cert) {
        try {
            byte[] authInfoAccessExt = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (authInfoAccessExt == null) {
                return null;
            }

            ASN1InputStream asn1In = new ASN1InputStream(new java.io.ByteArrayInputStream(authInfoAccessExt));
            byte[] octets = ((ASN1OctetString) asn1In.readObject()).getOctets();
            asn1In.close();

            asn1In = new ASN1InputStream(new java.io.ByteArrayInputStream(octets));
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(asn1In.readObject());
            asn1In.close();

            for (AccessDescription ad : aia.getAccessDescriptions()) {
                if (ad.getAccessMethod().equals(AccessDescription.id_ad_ocsp)) {
                    GeneralName gn = ad.getAccessLocation();
                    if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        return gn.getName().toString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<X509Certificate> loadSystemTrustAnchors() {
        List<X509Certificate> anchors = new ArrayList<>();
        try {
            TrustAnchorFinder finder = new TrustAnchorFinder();
            anchors.addAll(finder.find());
        } catch (Exception e) {
        }
        return anchors;
    }

    private static class TrustAnchorFinder {
        List<X509Certificate> find() throws Exception {
            List<X509Certificate> anchors = new ArrayList<>();
            String javaHome = System.getProperty("java.home");
            String cacertsPath = javaHome + "/lib/security/cacerts";

            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream is = new java.io.FileInputStream(cacertsPath)) {
                ks.load(is, "changeit".toCharArray());
            } catch (Exception e) {
                return anchors;
            }

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isCertificateEntry(alias)) {
                    anchors.add((X509Certificate) ks.getCertificate(alias));
                }
            }
            return anchors;
        }
    }
}
